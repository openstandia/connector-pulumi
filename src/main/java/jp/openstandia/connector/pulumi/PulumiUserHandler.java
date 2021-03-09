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

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.objects.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jp.openstandia.connector.pulumi.PulumiUtils.*;

public class PulumiUserHandler implements PulumiObjectHandler {

    public static final ObjectClass USER_OBJECT_CLASS = new ObjectClass("user");

    private static final Log LOGGER = Log.getLog(PulumiUserHandler.class);

    // Unique and unchangeable email
    // Also, it's case-sensitive
    static final String ATTR_EMAIL = "email";

    // Unique and unchangeable username
    // Also, it's case-sensitive
    static final String ATTR_USERNAME = "githubLogin";

    // Attributes
    static final String ATTR_NAME = "name";
    public static final String ATTR_ROLE = "role"; // member, admin
    static final String ATTR_AVATAR_URL = "avatarUrl";

    // Association
    public static final String ATTR_TEAMS = "teams";

    private final PulumiConfiguration configuration;
    private final PulumiClient client;
    private final PulumiAssociationHandler associationHandler;
    private final PulumiSchema schema;

    public PulumiUserHandler(PulumiConfiguration configuration, PulumiClient client,
                             Map<String, AttributeInfo> schema) {
        this.configuration = configuration;
        this.client = client;
        this.schema = new PulumiSchema(configuration, client);
        this.associationHandler = new PulumiAssociationHandler(configuration, client, this.schema);
    }

    public static ObjectClassInfo createSchema() {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(USER_OBJECT_CLASS.getObjectClassValue());

        // __UID__ and __NAME__ are the same
        // email (__UID__)
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(Uid.NAME)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .setNativeName(ATTR_EMAIL)
                        .build()
        );
        // email (__NAME__)
        AttributeInfoBuilder usernameBuilder = AttributeInfoBuilder.define(Name.NAME)
                .setRequired(true)
                .setUpdateable(false)
                .setNativeName(ATTR_EMAIL);
        builder.addAttributeInfo(usernameBuilder.build());

        // __ENABLE__ attribute
        // Not supported

        // __PASSWORD__ attribute
        // Not supported

        // Attributes
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_ROLE)
                        .setRequired(false)
                        .build()
        );

        // Readonly attributes
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_USERNAME)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .build()
        );
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_NAME)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .build()
        );
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_AVATAR_URL)
                        .setRequired(false)
                        .setCreateable(false)
                        .setUpdateable(false)
                        .build()
        );

        // Association
        builder.addAttributeInfo(
                AttributeInfoBuilder.define(ATTR_TEAMS)
                        .setRequired(false)
                        .setMultiValued(true)
                        .setSubtype(AttributeInfo.Subtypes.STRING_CASE_IGNORE)
                        .setReturnedByDefault(false)
                        .build()
        );

        ObjectClassInfo userSchemaInfo = builder.build();

        LOGGER.ok("The constructed user schema: {0}", userSchemaInfo);

        return userSchemaInfo;
    }

    /**
     * @param attributes
     * @return
     */
    @Override
    public Uid create(Set<Attribute> attributes) {
        // For use invitation, it only needs email.
        Set<Attribute> userAttrs = attributes.stream().filter(a -> a.is(Name.NAME)).collect(Collectors.toSet());

        Uid newUid = client.createUser(schema, userAttrs);

        // Team
        // Can't assign the team before finishing the invitation.

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
        client.updateUser(schema, uid, modifications, options);

        return null;
    }

    /**
     * @param uid
     * @param options
     */
    @Override
    public void delete(Uid uid, OperationOptions options) {
        client.deleteUser(schema, uid, options);
    }

    @Override
    public void query(PulumiFilter filter, ResultsHandler resultsHandler, OperationOptions options) {
        // Create full attributesToGet by RETURN_DEFAULT_ATTRIBUTES + ATTRIBUTES_TO_GET
        Set<String> attributesToGet = createFullAttributesToGet(schema.userSchema, options);
        boolean allowPartialAttributeValues = shouldAllowPartialAttributeValues(options);

        if (filter != null && (filter.isByUid() || filter.isByName())) {
            get(filter.attributeValue, resultsHandler, options, attributesToGet, allowPartialAttributeValues);
            return;
        }

        client.getUsers(schema,
                (member) -> resultsHandler.handle(toConnectorObject(member, attributesToGet, allowPartialAttributeValues)),
                options, attributesToGet, -1);
    }


    private void get(String username, ResultsHandler resultsHandler, OperationOptions options, Set<String> attributesToGet, boolean allowPartialAttributeValues) {
        PulumiClient.PulumiMemberRepresentation member = client.getUser(schema, new Uid(username), options,
                attributesToGet);

        if (member != null) {
            resultsHandler.handle(toConnectorObject(member, attributesToGet, allowPartialAttributeValues));
        }
    }

    private ConnectorObject toConnectorObject(PulumiClient.PulumiMemberRepresentation member,
                                              Set<String> attributesToGet, boolean allowPartialAttributeValues) {

        final ConnectorObjectBuilder builder = new ConnectorObjectBuilder()
                .setObjectClass(USER_OBJECT_CLASS)
                // Need to set __NAME__ because it throws IllegalArgumentException
                .setUid(member.user.email)
                .setName(member.user.email);

        // Attributes
        if (shouldReturn(attributesToGet, ATTR_ROLE)) {
            builder.addAttribute(AttributeBuilder.build(ATTR_ROLE, member.role));
        }

        // Readonly attributes
        if (shouldReturn(attributesToGet, ATTR_NAME)) {
            builder.addAttribute(AttributeBuilder.build(ATTR_NAME, member.user.name));
        }
        if (shouldReturn(attributesToGet, ATTR_AVATAR_URL)) {
            builder.addAttribute(AttributeBuilder.build(ATTR_AVATAR_URL, member.user.avatarUrl));
        }
        if (shouldReturn(attributesToGet, ATTR_USERNAME)) {
            builder.addAttribute(AttributeBuilder.build(ATTR_USERNAME, member.user.githubLogin));
        }

        if (allowPartialAttributeValues) {
            // Suppress fetching associations
            LOGGER.ok("Suppress fetching associations because return partial attribute values is requested");

            Stream.of(ATTR_TEAMS).forEach(attrName -> {
                AttributeBuilder ab = new AttributeBuilder();
                ab.setName(attrName).setAttributeValueCompleteness(AttributeValueCompleteness.INCOMPLETE);
                ab.addValue(Collections.EMPTY_LIST);
                builder.addAttribute(ab.build());
            });

        } else {
            if (attributesToGet == null) {
                // Suppress fetching associations default
                LOGGER.ok("Suppress fetching associations because returned by default is true");

            } else {
                if (shouldReturn(attributesToGet, ATTR_TEAMS)) {
                    // Can't resolve teams while inviting the user
                    if (member.user.githubLogin != null) {
                        // Fetch teams
                        LOGGER.ok("Fetching teams because attributes to get is requested");

                        List<String> teams = associationHandler.getTeamsForUser(member.user.githubLogin);
                        builder.addAttribute(ATTR_TEAMS, teams);
                    }
                }
            }
        }

        return builder.build();
    }
}
