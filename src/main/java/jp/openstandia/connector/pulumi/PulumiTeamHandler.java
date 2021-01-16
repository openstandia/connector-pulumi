/*
 *  Copyright Nomura Research Institute, Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jp.openstandia.connector.pulumi;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.objects.*;

import java.util.Set;

import static jp.openstandia.connector.pulumi.PulumiUtils.*;

public class PulumiTeamHandler implements PulumiObjectHandler {

    public static final ObjectClass TEAM_OBJECT_CLASS = new ObjectClass("team");

    private static final Log LOGGER = Log.getLog(PulumiTeamHandler.class);

    // Unique and unchangeable
    // Also, it's case-sensitive
    static final String ATTR_NAME = "name";

    // Attributes
    public static final String ATTR_DISPLAY_NAME = "displayName";
    public static final String ATTR_DESCRIPTION = "description";

    private final PulumiConfiguration configuration;
    private final PulumiClient client;
    private final PulumiSchema schema;
    private final PulumiAssociationHandler associationHandler;

    public PulumiTeamHandler(PulumiConfiguration configuration, PulumiClient client) {
        this.configuration = configuration;
        this.client = client;
        this.schema = new PulumiSchema(configuration, client);
        this.associationHandler = new PulumiAssociationHandler(configuration, client, this.schema);
    }

    public static ObjectClassInfo createSchema() {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(TEAM_OBJECT_CLASS.getObjectClassValue());

        // __UID__ and __NAME__ are the same
        // identifier __UID__
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(Uid.NAME)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setNativeName(ATTR_NAME)
                        .build());
        // identifier __NAME__
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(Name.NAME)
                        .setRequired(true)
                        .setUpdateable(false)
                        .setNativeName(ATTR_NAME)
                        .build());

        // Attributes
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_DISPLAY_NAME)
                        .setRequired(false) // Must be optional
                        .setCreateable(true)
                        .setUpdateable(true)
                        .build());
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_DESCRIPTION)
                        .setRequired(false) // Must be optional
                        .setCreateable(true)
                        .setUpdateable(true)
                        .build());

        ObjectClassInfo teamSchemaInfo = builder.build();

        LOGGER.info("The constructed team schema: {0}", teamSchemaInfo);

        return teamSchemaInfo;
    }

    /**
     * @param attributes
     * @return
     * @throws AlreadyExistsException Object with the specified _NAME_ already exists.
     *                                Or there is a similar violation in any of the object attributes that
     *                                cannot be distinguished from AlreadyExists situation.
     */
    @Override
    public Uid create(Set<Attribute> attributes) throws AlreadyExistsException {
        Uid newUid = client.createTeam(schema, attributes);

        return newUid;
    }

    /**
     * @param uid
     * @param modifications
     * @param options
     * @return
     */
    @Override
    public Set<AttributeDelta> updateDelta(Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        client.updateTeam(schema, uid, modifications, options);

        return null;
    }

    /**
     * @param uid
     * @param options
     */
    @Override
    public void delete(Uid uid, OperationOptions options) {
        client.deleteTeam(schema, uid, options);
    }

    /**
     * @param filter
     * @param resultsHandler
     * @param options
     */
    @Override
    public void query(PulumiFilter filter, ResultsHandler resultsHandler, OperationOptions options) {
        // Create full attributesToGet by RETURN_DEFAULT_ATTRIBUTES + ATTRIBUTES_TO_GET
        Set<String> attributesToGet = createFullAttributesToGet(schema.teamSchema, options);
        boolean allowPartialAttributeValues = shouldAllowPartialAttributeValues(options);

        if (filter != null && (filter.isByUid() || filter.isByName())) {
            get(filter.attributeValue, resultsHandler, options, attributesToGet, allowPartialAttributeValues);
            return;
        }

        client.getTeams(schema, team -> resultsHandler.handle(toConnectorObject(team, attributesToGet, allowPartialAttributeValues)), options, attributesToGet, -1);
    }


    private void get(String teamName, ResultsHandler resultsHandler, OperationOptions options, Set<String> attributesToGet, boolean allowPartialAttributeValues) {
        PulumiClient.PulumiTeamWithMembersRepresentation team = client.getTeam(schema, new Uid(teamName), options, attributesToGet);

        if (team != null) {
            resultsHandler.handle(toConnectorObject(team, attributesToGet, allowPartialAttributeValues));
        }
    }

    private ConnectorObject toConnectorObject(PulumiClient.PulumiTeamRepresentation team,
                                              Set<String> attributesToGet, boolean allowPartialAttributeValues) {
        PulumiClient.PulumiTeamWithMembersRepresentation t = new PulumiClient.PulumiTeamWithMembersRepresentation();
        t.kind = team.kind;
        t.name = team.name;
        t.displayName = team.displayName;
        t.description = team.description;
        t.members = null;

        return toConnectorObject(t, attributesToGet, allowPartialAttributeValues);
    }

    private ConnectorObject toConnectorObject(PulumiClient.PulumiTeamWithMembersRepresentation team,
                                              Set<String> attributesToGet, boolean allowPartialAttributeValues) {

        final ConnectorObjectBuilder builder = new ConnectorObjectBuilder()
                .setObjectClass(TEAM_OBJECT_CLASS)
                // Need to set __UID__ and __NAME__ because it throws IllegalArgumentException
                .setUid(team.name)
                .setName(team.name);

        // Attributes
        if (shouldReturn(attributesToGet, ATTR_DISPLAY_NAME)) {
            if (!StringUtil.isEmpty(team.displayName)) {
                builder.addAttribute(AttributeBuilder.build(ATTR_DISPLAY_NAME, team.displayName));
            }
        }
        if (shouldReturn(attributesToGet, ATTR_DESCRIPTION)) {
            if (!StringUtil.isEmpty(team.description)) {
                builder.addAttribute(AttributeBuilder.build(ATTR_DESCRIPTION, team.description));
            }
        }

        return builder.build();
    }
}
