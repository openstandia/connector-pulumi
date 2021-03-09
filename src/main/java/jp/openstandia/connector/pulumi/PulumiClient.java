package jp.openstandia.connector.pulumi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static jp.openstandia.connector.pulumi.PulumiTeamHandler.*;
import static jp.openstandia.connector.pulumi.PulumiUserHandler.ATTR_ROLE;
import static jp.openstandia.connector.pulumi.PulumiUserHandler.USER_OBJECT_CLASS;


public interface PulumiClient {
    void test();

    default String getInvitationEndpointURL(PulumiConfiguration configuration) {
        String url = configuration.getPulumiConsoleURL();
        return String.format("%s/invites", url);
    }

    default String getInvitationEndpointURL(PulumiConfiguration configuration, String invitationId) {
        String url = configuration.getPulumiConsoleURL();
        return String.format("%s/invites/%s", url, invitationId);
    }

    default String getUsersEndpointURL(PulumiConfiguration configuration) {
        String url = configuration.getPulumiURL();
        return String.format("%s/members?type=frontend", url);
    }

    default String getUserEndpointURL(PulumiConfiguration configuration, String username) {
        String url = configuration.getPulumiURL();
        return String.format("%s/members/%s", url, username);
    }

    default String getCreateTeamEndpointURL(PulumiConfiguration configuration) {
        String url = configuration.getPulumiURL();
        return String.format("%s/teams/pulumi", url);
    }

    default String getTeamsEndpointURL(PulumiConfiguration configuration) {
        String url = configuration.getPulumiURL();
        return String.format("%s/teams", url);
    }

    default String getTeamEndpointURL(PulumiConfiguration configuration, Uid teamUid) {
        return getTeamEndpointURL(configuration, teamUid.getUidValue());
    }

    default String getTeamEndpointURL(PulumiConfiguration configuration, String teamName) {
        String url = configuration.getPulumiURL();
        return String.format("%s/teams/%s", url, teamName);
    }

    default PulumiInvitationRepresentation createInvitation(PulumiSchema schema, Set<Attribute> attributes) {
        PulumiInvitationRepresentation invitation = new PulumiInvitationRepresentation();

        for (Attribute attr : attributes) {
            // Need to get the value from __NAME__ (not __UID__)
            if (attr.getName().equals(Name.NAME)) {
                invitation.email = AttributeUtil.getStringValue(attr);

            } else if (attr.getName().equals(ATTR_ROLE)) {
                invitation.role = AttributeUtil.getStringValue(attr);

            } else {
                throw new InvalidAttributeValueException(String.format("Pulumi doesn't support to set '%s' attribute of %s",
                        attr.getName(), USER_OBJECT_CLASS.getObjectClassValue()));
            }
        }

        if (invitation.role == null) {
            invitation.role = "member";
        }
        if (invitation.email == null) {
            throw new InvalidAttributeValueException("Invalid invitation due to no email");
        }

        return invitation;
    }

    default PulumiUpdateUserOperation createUpdateUser(PulumiSchema schema, Set<AttributeDelta> modifications) {
        Optional<String> role = modifications.stream()
                .filter(m -> m.is(ATTR_ROLE))
                .map(m -> AttributeDeltaUtil.getStringValue(m))
                .findFirst();
        if (!role.isPresent()) {
            return null;
        }

        PulumiUpdateUserOperation op = new PulumiUpdateUserOperation();
        op.role = role.get();

        return op;
    }

    default PulumiTeamRepresentation newTeam(PulumiSchema schema, Set<Attribute> attributes) {
        PulumiTeamRepresentation team = new PulumiTeamRepresentation();
        team.displayName = "";
        team.description = "";

        for (Attribute attr : attributes) {
            // Need to get the value from __NAME__ (not __UID__)
            if (attr.getName().equals(Name.NAME)) {
                team.name = AttributeUtil.getStringValue(attr);

            } else if (attr.getName().equals(ATTR_DISPLAY_NAME)) {
                team.displayName = AttributeUtil.getStringValue(attr);

            } else if (attr.getName().equals(ATTR_DESCRIPTION)) {
                team.description = AttributeUtil.getStringValue(attr);

            } else {
                throw new InvalidAttributeValueException(String.format("Pulumi doesn't support to set '%s' attribute of %s",
                        attr.getName(), TEAM_OBJECT_CLASS.getObjectClassValue()));
            }
        }

        if (team.name == null) {
            throw new InvalidAttributeValueException("Invalid team due to no name when creating");
        }

        return team;
    }

    void close();

    // User

    /**
     * @param schema
     * @param createAttributes
     * @return Username of the created user. Caution! Don't include Name object in the Uid because it throws
     * SchemaException with "No definition for ConnId NAME attribute found in definition crOCD ({http://midpoint.evolveum.com/xml/ns/public/resource/instance-3}User)
     * @throws AlreadyExistsException
     */
    Uid createUser(PulumiSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException;

    void updateUser(PulumiSchema schema, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException;

    void deleteUser(PulumiSchema schema, Uid uid, OperationOptions options) throws UnknownUidException;

    void getUsers(PulumiSchema schema, PulumiQueryHandler<PulumiMemberRepresentation> handler, OperationOptions options, Set<String> attributesToGet, int queryPageSize);

    PulumiMemberRepresentation getUser(PulumiSchema schema, Uid uid, OperationOptions options, Set<String> attributesToGet);

    void getTeamsForUser(PulumiSchema schema, String username, PulumiQueryHandler<PulumiTeamRepresentation> handler);

    // Team

    /**
     * @param schema
     * @param createAttributes
     * @return Identifier of the created team. Caution! Don't include Name object in the Uid because it throws
     * SchemaException with "No definition for ConnId NAME attribute found in definition crOCD ({http://midpoint.evolveum.com/xml/ns/public/resource/instance-3}team)
     * @throws AlreadyExistsException
     */
    Uid createTeam(PulumiSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException;

    void updateTeam(PulumiSchema schema, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException;

    void deleteTeam(PulumiSchema schema, Uid uid, OperationOptions options) throws UnknownUidException;

    void getTeams(PulumiSchema schema, PulumiQueryHandler<PulumiTeamRepresentation> handler, OperationOptions options, Set<String> attributesToGet, int queryPageSize);

    PulumiTeamWithMembersRepresentation getTeam(PulumiSchema schema, Uid uid, OperationOptions options, Set<String> attributesToGet);

    // JSON Representation

    class PulumiInvitationRepresentation {
        public String email;
        public String role;
    }

    class PulumiUpdateUserOperation {
        public String role;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiInvitesRepresentation {
        public List<PulumiInviteRepresentation> invites;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiInviteRepresentation {
        public String id;
        public String email;
        public String role;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiMembersRepresentation {
        public List<PulumiMemberRepresentation> members;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiMemberRepresentation {
        public String role;
        public PulumiUserRepresentation user;

        @JsonIgnore
        public String invitationId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiUserRepresentation {
        public String name;
        public String githubLogin;
        public String avatarUrl;
        public String email;
    }

    class PulumiUpdateTeamOperation {
        public String newDisplayName;
        public String newDescription;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiTeamsRepresentation {
        public List<PulumiTeamRepresentation> teams;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiTeamRepresentation {
        public String kind;
        public String name;
        public String displayName;
        public String description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiTeamWithMembersRepresentation {
        public String kind;
        public String name;
        public String displayName;
        public String description;
        public List<PulumiTeamMemberRepresentation> members;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiTeamMemberRepresentation {
        public String name;
        public String githubLogin;
        public String avatarUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class PulumiErrorRepresentation {
        public int code;
        public String message;

        public boolean isAlreadyExists() {
            return code == 409;
        }

        public boolean isUnauthorized() {
            return code == 401;
        }

        public boolean isNotFound() {
            return code == 404;
        }
    }
}