package jp.openstandia.connector.pulumi.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.openstandia.connector.pulumi.*;
import okhttp3.*;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static jp.openstandia.connector.pulumi.PulumiTeamHandler.*;
import static jp.openstandia.connector.pulumi.PulumiUserHandler.USER_OBJECT_CLASS;

public class PulumiRESTClient implements PulumiClient {

    private static final Log LOG = Log.getLog(PulumiRESTClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String instanceName;
    private final PulumiConfiguration configuration;
    private final OkHttpClient httpClient;

    public PulumiRESTClient(String instanceName, PulumiConfiguration configuration, OkHttpClient httpClient) {
        this.instanceName = instanceName;
        this.configuration = configuration;
        this.httpClient = httpClient;
    }

    @Override
    public void test() {
        try (Response response = get(configuration.getPulumiSelfURL())) {
            if (response.code() != 200) {
                // Something wrong..
                String body = response.body().string();
                throw new ConnectionFailedException(String.format("Unexpected authentication response. statusCode: %s, body: %s",
                        response.code(),
                        body));
            }

            LOG.info("[{0}] Pulumi connector's connection test is OK", instanceName);

        } catch (IOException e) {
            throw new ConnectionFailedException("Cannot connect to pulumi REST API", e);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public Uid createUser(PulumiSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException {
        PulumiInvitationRepresentation invitation = createInvitation(schema, createAttributes);

        try (Response response = post(getInvitationEndpointURL(configuration), invitation)) {
            if (response.code() == 400) {
                throw new InvalidAttributeValueException(String.format("Bad request when inviting pulumi user. email: %s", invitation.email));
            }

            if (response.code() != 204) {
                throw new ConnectorIOException(String.format("Failed to invite pulumi user: %s, statusCode: %d",
                        invitation.email, response.code()));
            }

            // Created
            // Don't include Name object
            return new Uid(invitation.email);

        } catch (IOException e) {
            throw new ConnectorIOException("Failed to call pulumi invite user API", e);
        }
    }

    @Override
    public void updateUser(PulumiSchema schema, Uid userUid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException {
        PulumiMemberRepresentation member = getUser(schema, userUid, options, Collections.emptySet());
        if (member == null) {
            throw new UnknownUidException(userUid, USER_OBJECT_CLASS);
        }

        if (member.invitationId != null) {
            // Can't update the inviting user
            throw new InvalidAttributeValueException("Can't update the pulumi user due to pending: " + userUid.getUidValue());
        }

        PulumiUpdateUserOperation op = createUpdateUser(schema, modifications);
        if (op == null) {
            return;
        }

        callUpdate(USER_OBJECT_CLASS, getUserEndpointURL(configuration, member.user.githubLogin), userUid, op);
    }

    @Override
    public void deleteUser(PulumiSchema schema, Uid userUid, OperationOptions options) throws UnknownUidException {
        PulumiMemberRepresentation member = getUser(schema, userUid, options, Collections.emptySet());
        if (member == null) {
            throw new UnknownUidException(userUid, USER_OBJECT_CLASS);
        }

        if (member.invitationId != null) {
            callDelete(USER_OBJECT_CLASS, getInvitationEndpointURL(configuration, member.invitationId), userUid);

        } else {
            callDelete(USER_OBJECT_CLASS, getUserEndpointURL(configuration, member.user.githubLogin), userUid);
        }
    }

    @Override
    public void getUsers(PulumiSchema schema, PulumiQueryHandler<PulumiMemberRepresentation> handler, OperationOptions options, Set<String> attributesToGet, int queryPageSize) {
        // Lookup from inviting users
        try (Response response = get(getInvitationEndpointURL(configuration))) {
            if (response.code() != 200) {
                throw new ConnectorIOException(String.format("Failed to get pulumi inviting users. statusCode: %d", response.code()));
            }

            // Success
            PulumiInvitesRepresentation users = MAPPER.readValue(response.body().byteStream(), PulumiInvitesRepresentation.class);
            for (PulumiInviteRepresentation invite : users.invites) {
                PulumiMemberRepresentation member = new PulumiMemberRepresentation();
                member.invitationId = invite.id;
                member.role = invite.role;
                member.user = new PulumiUserRepresentation();
                member.user.email = invite.email;

                if (!handler.handle(member)) {
                    break;
                }
            }

        } catch (IOException e) {
            throw new ConnectorIOException("Failed to call pulumi get inviting users API", e);
        }

        // Lookup from members
        try (Response response = get(getUsersEndpointURL(configuration))) {
            if (response.code() != 200) {
                throw new ConnectorIOException(String.format("Failed to get pulumi users. statusCode: %d", response.code()));
            }

            // Success
            PulumiMembersRepresentation users = MAPPER.readValue(response.body().byteStream(), PulumiMembersRepresentation.class);
            for (PulumiMemberRepresentation member : users.members) {
                if (!handler.handle(member)) {
                    break;
                }
            }

        } catch (IOException e) {
            throw new ConnectorIOException("Failed to call pulumi get users API", e);
        }
    }

    @Override
    public PulumiMemberRepresentation getUser(PulumiSchema schema, Uid uid, OperationOptions options, Set<String> attributesToGet) {
        AtomicReference<PulumiMemberRepresentation> result = new AtomicReference<>();

        getUsers(schema, (member) -> {
            if (member.user.email.equalsIgnoreCase(uid.getUidValue())) {
                result.set(member);
                return false;
            }
            return true;
        }, options, attributesToGet, -1);

        return result.get();
    }

    @Override
    public Uid createTeam(PulumiSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException {
        PulumiTeamRepresentation team = newTeam(schema, createAttributes);

        try (Response response = post(getCreateTeamEndpointURL(configuration), team)) {
            if (response.code() == 400) {
                PulumiErrorRepresentation error = MAPPER.readValue(response.body().byteStream(), PulumiErrorRepresentation.class);
                if (error.isAlreadyExists()) {
                    throw new AlreadyExistsException(String.format("Team '%s' already exists.", team.name));
                }
            }

            if (response.code() != 201) {
                throw new ConnectorIOException(String.format("Failed to create pulumi team: %s, statusCode: %d", team.name, response.code()));
            }

            // Created
            // Don't include Name
            return new Uid(team.name);

        } catch (IOException e) {
            throw new ConnectorIOException("Failed to call pulumi REST API", e);
        }
    }

    @Override
    public void updateTeam(PulumiSchema schema, Uid teamUid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException {
        PulumiUpdateTeamOperation target = new PulumiUpdateTeamOperation();

        // Apply delta
        modifications.stream().forEach(delta -> {
            if (delta.getName().equals(ATTR_DISPLAY_NAME)) {
                target.newDisplayName = PulumiUtils.toResourceValue(delta);

            } else if (delta.getName().equals(ATTR_DESCRIPTION)) {
                target.newDescription = PulumiUtils.toResourceValue(delta);
            }
        });

        callUpdate(TEAM_OBJECT_CLASS, getTeamEndpointURL(configuration, teamUid), teamUid, target);
    }

    @Override
    public void deleteTeam(PulumiSchema schema, Uid teamUid, OperationOptions options) throws UnknownUidException {
        callDelete(USER_OBJECT_CLASS, getTeamEndpointURL(configuration, teamUid), teamUid);
    }

    @Override
    public void getTeams(PulumiSchema schema, PulumiQueryHandler<PulumiTeamRepresentation> handler, OperationOptions options, Set<String> attributesToGet, int queryPageSize) {
        try (Response response = get(getTeamsEndpointURL(configuration))) {
            if (response.code() != 200) {
                throw new ConnectorIOException(String.format("Failed to get pulumi teams. statusCode: %d", response.code()));
            }

            // Success
            PulumiTeamsRepresentation teams = MAPPER.readValue(response.body().byteStream(), PulumiTeamsRepresentation.class);
            teams.teams.stream().forEach(team -> handler.handle(team));

        } catch (IOException e) {
            throw new ConnectorIOException("Failed to call pulumi get teams API", e);
        }
    }

    @Override
    public PulumiTeamWithMembersRepresentation getTeam(PulumiSchema schema, Uid uid, OperationOptions options, Set<String> attributesToGet) {
        try {
            Response response = get(getTeamEndpointURL(configuration, uid));

            if (response.code() == 404) {
                // Don't throw
                return null;
            }

            if (response.code() != 200) {
                throw new ConnectorIOException(String.format("Failed to get pulumi team. statusCode: %d", response.code()));
            }

            // Success
            PulumiTeamWithMembersRepresentation team = MAPPER.readValue(response.body().byteStream(), PulumiTeamWithMembersRepresentation.class);
            return team;

        } catch (IOException e) {
            throw new ConnectorIOException("Failed to call pulumi get team API", e);
        }
    }

    @Override
    public void getUsersForTeam(PulumiTeamWithMembersRepresentation team, PulumiQueryHandler<String> handler) {
        try {
            if (team.members == null) {
                // Fetch team members
                Response response = get(getTeamEndpointURL(configuration, new Uid(team.name)));
                if (response.code() == 404) {
                    throw new UnknownUidException(new Uid(team.name), TEAM_OBJECT_CLASS);
                }
                if (response.code() != 200) {
                    throw new ConnectorIOException(String.format("Failed to get pulumi team. statusCode: %d", response.code()));
                }
                team = MAPPER.readValue(response.body().byteStream(), PulumiTeamWithMembersRepresentation.class);
            }

            // Unfortunately, fetch all members here because team API doesn't return email.
            Map<String, String> usernameToEmail = new HashMap<>();

            try (Response response = get(getUsersEndpointURL(configuration))) {
                if (response.code() != 200) {
                    throw new ConnectorIOException(String.format("Failed to get pulumi users. statusCode: %d", response.code()));
                }

                // Success
                PulumiMembersRepresentation users = MAPPER.readValue(response.body().byteStream(), PulumiMembersRepresentation.class);
                for (PulumiMemberRepresentation member : users.members) {
                    usernameToEmail.put(member.user.githubLogin, member.user.email);
                }
            } catch (IOException e) {
                throw new ConnectorIOException("Failed to call pulumi get users API", e);
            }

            team.members.stream().forEach(m -> {
                String email = usernameToEmail.get(m.githubLogin);
                handler.handle(email);
            });

        } catch (IOException e) {
            throw new ConnectorIOException("Failed to call pulumi team members API", e);
        }
    }

    @Override
    public void assignUsersToTeam(Uid teamUid, List<String> addUserEmails, List<String> removeUserEmails) {
        callAssign(TEAM_OBJECT_CLASS, getTeamEndpointURL(configuration, teamUid),
                teamUid, addUserEmails, removeUserEmails);
    }

    // Utilities

    protected void callUpdate(ObjectClass objectClass, String url, Uid uid, Object target) {
        try (Response response = patch(url, target)) {
            if (response.code() == 400) {
                throw new InvalidAttributeValueException(String.format("Bad request when updating %s: %s, response: %s",
                        objectClass.getObjectClassValue(), uid.getUidValue(), toBody(response)));
            }

            if (response.code() == 404) {
                throw new UnknownUidException(uid, objectClass);
            }

            if (response.code() != 204) {
                throw new ConnectorIOException(String.format("Failed to update pulumi %s: %s, statusCode: %d, response: %s",
                        objectClass.getObjectClassValue(), uid.getUidValue(), response.code(), toBody(response)));
            }

            // Success

        } catch (IOException e) {
            throw new ConnectorIOException(String.format("Failed to update pulumi %s: %s",
                    objectClass.getObjectClassValue(), uid.getUidValue()), e);
        }
    }

    private String toBody(Response response) {
        ResponseBody resBody = response.body();
        if (resBody == null) {
            return null;
        }
        try {
            return resBody.string();
        } catch (IOException e) {
            LOG.error(e, "Unexpected pulumi REST API response");
            return "<failed_to_parse_response>";
        }
    }

    /**
     * Generic delete method.
     *
     * @param objectClass
     * @param url
     * @param uid
     */
    protected void callDelete(ObjectClass objectClass, String url, Uid uid) {
        try (Response response = delete(url)) {
            if (response.code() == 404) {
                throw new UnknownUidException(uid, objectClass);
            }

            if (response.code() != 204) {
                throw new ConnectorIOException(String.format("Failed to delete pulumi %s: %s, statusCode: %d, response: %s",
                        objectClass.getObjectClassValue(), uid.getUidValue(), response.code(), toBody(response)));
            }

            // Success

        } catch (IOException e) {
            throw new ConnectorIOException(String.format("Failed to delete pulumi %s: %s",
                    objectClass.getObjectClassValue(), uid.getUidValue()), e);
        }
    }

    /**
     * Team assign/unassign method.
     *
     * @param objectClass
     * @param url
     * @param uid
     * @param addUserEmails
     * @param removeUserEmails
     */
    protected void callAssign(ObjectClass objectClass, String url, Uid uid, List<String> addUserEmails, List<String> removeUserEmails) {
        // Unfortunately, fetch all users here to resolve username.
        // The key must be lowercase for case-insensitive.
        Map<String, String> emailToUsername = new HashMap<>();

        try (Response response = get(getUsersEndpointURL(configuration))) {
            if (response.code() != 200) {
                throw new ConnectorIOException(String.format("Failed to get pulumi users. statusCode: %d", response.code()));
            }

            // Success
            PulumiMembersRepresentation users = MAPPER.readValue(response.body().byteStream(), PulumiMembersRepresentation.class);
            for (PulumiMemberRepresentation member : users.members) {
                emailToUsername.put(member.user.email.toLowerCase(), member.user.githubLogin);
            }
        } catch (IOException e) {
            throw new ConnectorIOException("Failed to call pulumi get users API", e);
        }

        // assign
        for (String email : addUserEmails) {
            String username = emailToUsername.get(email.toLowerCase()); // Need lowerCase for case-insensitive
            if (username == null) {
                LOG.warn("Unknown pulumi user when assign to team. email: %s, team: %s", email, uid.getUidValue());
                continue;
            }

            Map<String, String> body = new HashMap<>();
            body.put("memberAction", "add");
            body.put("member", username);

            try (Response response = patch(url, body)) {
                if (response.code() == 404) {
                    // Missing the team
                    throw new UnknownUidException(uid, objectClass);
                }

                if (response.code() != 204) {
                    throw new ConnectorIOException(String.format("Failed to assign %s to %s, add: %s, remove: %s, statusCode: %d, response: %s",
                            objectClass.getObjectClassValue(), uid.getUidValue(), addUserEmails, removeUserEmails, response.code(), toBody(response)));
                }
                // Success

            } catch (IOException e) {
                throw new ConnectorIOException(String.format("Failed to assign %s to %s, add: %s, remove: %s",
                        objectClass.getObjectClassValue(), uid.getUidValue(), addUserEmails, removeUserEmails), e);
            }
        }

        // unassign
        for (String email : removeUserEmails) {
            String username = emailToUsername.get(email.toLowerCase()); // Need lowerCase for case-insensitive
            if (username == null) {
                LOG.warn("Unknown pulumi user when unassign to team. email: %s, team: %s", email, uid.getUidValue());
                continue;
            }

            Map<String, String> body = new HashMap<>();
            body.put("memberAction", "remove");
            body.put("member", username);

            try (Response response = patch(url, body)) {
                if (response.code() == 404) {
                    // Missing the team
                    throw new UnknownUidException(uid, objectClass);
                }

                if (response.code() != 204) {
                    throw new ConnectorIOException(String.format("Failed to unassign %s to %s, add: %s, remove: %s, statusCode: %d, response: %s",
                            objectClass.getObjectClassValue(), uid.getUidValue(), addUserEmails, removeUserEmails, response.code(), toBody(response)));
                }
                // Success

            } catch (IOException e) {
                throw new ConnectorIOException(String.format("Failed to unassign %s to %s, add: %s, remove: %s",
                        objectClass.getObjectClassValue(), uid.getUidValue(), addUserEmails, removeUserEmails), e);
            }
        }
    }

    private RequestBody createJsonRequestBody(Object body) {
        String bodyString;
        try {
            bodyString = MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new ConnectorIOException("Failed to write request json body", e);
        }

        return RequestBody.create(bodyString, MediaType.parse("application/json; charset=UTF-8"));
    }

    private void throwExceptionIfUnauthorized(Response response) throws ConnectorIOException {
        if (response.code() == 401) {
            throw new ConnectionFailedException("Cannot authenticate to the pulumi REST API: " + response.message());
        }
    }

    private void throwExceptionIfServerError(Response response) throws ConnectorIOException {
        if (response.code() >= 500 && response.code() <= 599) {
            try {
                String body = response.body().string();
                throw new ConnectorIOException("Pulumi server error: " + body);
            } catch (IOException e) {
                throw new ConnectorIOException("Pulumi server error", e);
            }
        }
    }

    private Response get(String url) throws IOException {
        for (int i = 0; i < 2; i++) {
            final Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            final Response response = httpClient.newCall(request).execute();

            throwExceptionIfUnauthorized(response);
            throwExceptionIfServerError(response);

            return response;
        }

        throw new ConnectorIOException("Failed to call get API");
    }

    private Response post(String url, Object body) throws IOException {
        RequestBody requestBody = createJsonRequestBody(body);

        for (int i = 0; i < 2; i++) {
            final Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            final Response response = httpClient.newCall(request).execute();

            throwExceptionIfUnauthorized(response);
            throwExceptionIfServerError(response);

            return response;
        }

        throw new ConnectorIOException("Failed to call post API");
    }

    private Response put(String url, Object body) throws IOException {
        RequestBody requestBody = createJsonRequestBody(body);

        for (int i = 0; i < 2; i++) {
            final Request request = new Request.Builder()
                    .url(url)
                    .put(requestBody)
                    .build();

            final Response response = httpClient.newCall(request).execute();

            throwExceptionIfUnauthorized(response);
            throwExceptionIfServerError(response);

            return response;
        }

        throw new ConnectorIOException("Failed to call post API");
    }

    private Response patch(String url, Object body) throws IOException {
        RequestBody requestBody = createJsonRequestBody(body);

        for (int i = 0; i < 2; i++) {
            final Request request = new Request.Builder()
                    .url(url)
                    .patch(requestBody)
                    .build();

            final Response response = httpClient.newCall(request).execute();

            throwExceptionIfUnauthorized(response);
            throwExceptionIfServerError(response);

            return response;
        }

        throw new ConnectorIOException("Failed to call patch API");
    }

    private Response delete(String url) throws IOException {
        for (int i = 0; i < 2; i++) {
            final Request request = new Request.Builder()
                    .url(url)
                    .delete()
                    .build();

            final Response response = httpClient.newCall(request).execute();

            throwExceptionIfUnauthorized(response);
            throwExceptionIfServerError(response);

            return response;
        }

        throw new ConnectorIOException("Failed to call delete API");
    }
}
