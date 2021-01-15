package jp.openstandia.connector.pulumi;

import org.identityconnectors.framework.common.objects.*;

import java.util.Set;

public interface PulumiObjectHandler {

    Uid create(Set<Attribute> attributes);

    Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options);

    void delete(Uid uid, OperationOptions options);

    void query(PulumiFilter filter, ResultsHandler resultsHandler, OperationOptions options);


}
