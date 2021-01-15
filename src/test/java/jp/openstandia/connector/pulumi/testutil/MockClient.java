package jp.openstandia.connector.pulumi.testutil;

import jp.openstandia.connector.pulumi.PulumiClient;
import jp.openstandia.connector.pulumi.PulumiQueryHandler;
import jp.openstandia.connector.pulumi.PulumiSchema;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.List;
import java.util.Set;

public class MockClient implements PulumiClient {
    public static MockClient instance() {
        return new MockClient();
    }

    public void init() {
    }

    @Override
    public void test() {

    }

    @Override
    public void close() {

    }

    @Override
    public Uid createUser(PulumiSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException {
        return null;
    }

    @Override
    public void updateUser(PulumiSchema schema, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException {

    }

    @Override
    public void deleteUser(PulumiSchema schema, Uid uid, OperationOptions options) throws UnknownUidException {

    }

    @Override
    public void getUsers(PulumiSchema schema, PulumiQueryHandler<PulumiMemberRepresentation> handler, OperationOptions options, Set<String> attributesToGet, int queryPageSize) {

    }

    @Override
    public PulumiMemberRepresentation getUser(PulumiSchema schema, Uid uid, OperationOptions options, Set<String> attributesToGet) {
        return null;
    }

    @Override
    public Uid createTeam(PulumiSchema schema, Set<Attribute> createAttributes) throws AlreadyExistsException {
        return null;
    }

    @Override
    public void updateTeam(PulumiSchema schema, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) throws UnknownUidException {

    }

    @Override
    public void deleteTeam(PulumiSchema schema, Uid uid, OperationOptions options) throws UnknownUidException {

    }

    @Override
    public void getTeams(PulumiSchema schema, PulumiQueryHandler<PulumiTeamRepresentation> handler, OperationOptions options, Set<String> attributesToGet, int queryPageSize) {

    }

    @Override
    public PulumiTeamWithMembersRepresentation getTeam(PulumiSchema schema, Uid uid, OperationOptions options, Set<String> attributesToGet) {
        return null;
    }

    @Override
    public void getUsersForTeam(PulumiTeamWithMembersRepresentation team, PulumiQueryHandler<String> handler) {

    }

    @Override
    public void assignUsersToTeam(Uid teamUid, List<String> addUserEmails, List<String> removeUserEmails) {

    }

}
