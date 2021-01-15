package jp.openstandia.connector.pulumi;

import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.operations.SearchOp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Schema for Pulumi objects.
 *
 * @author Hiroyuki Wada
 */
public class PulumiSchema {

    private final PulumiConfiguration configuration;
    private final PulumiClient client;

    public final Schema schema;
    public final Map<String, AttributeInfo> userSchema;
    public final Map<String, AttributeInfo> teamSchema;

    public PulumiSchema(PulumiConfiguration configuration, PulumiClient client) {
        this.configuration = configuration;
        this.client = client;

        SchemaBuilder schemaBuilder = new SchemaBuilder(PulumiConnector.class);

        ObjectClassInfo userSchemaInfo = PulumiUserHandler.createSchema();
        schemaBuilder.defineObjectClass(userSchemaInfo);

        ObjectClassInfo teamSchemaInfo = PulumiTeamHandler.createSchema();
        schemaBuilder.defineObjectClass(teamSchemaInfo);

        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildAttributesToGet(), SearchOp.class);
        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildReturnDefaultAttributes(), SearchOp.class);

        schema = schemaBuilder.build();

        Map<String, AttributeInfo> userSchemaMap = new HashMap<>();
        for (AttributeInfo info : userSchemaInfo.getAttributeInfo()) {
            userSchemaMap.put(info.getName(), info);
        }

        Map<String, AttributeInfo> teamSchemaMap = new HashMap<>();
        for (AttributeInfo info : teamSchemaInfo.getAttributeInfo()) {
            teamSchemaMap.put(info.getName(), info);
        }

        this.userSchema = Collections.unmodifiableMap(userSchemaMap);
        this.teamSchema = Collections.unmodifiableMap(teamSchemaMap);
    }
}
