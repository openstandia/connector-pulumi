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

import java.util.ArrayList;
import java.util.List;

public class PulumiAssociationHandler {

    private static final Log LOGGER = Log.getLog(PulumiAssociationHandler.class);

    private final PulumiConfiguration configuration;
    private final PulumiClient client;
    private final PulumiSchema schema;

    public PulumiAssociationHandler(PulumiConfiguration configuration, PulumiClient client, PulumiSchema schema) {
        this.configuration = configuration;
        this.client = client;
        this.schema = schema;
    }

    public List<String> getTeamsForUser(String username) {
        List<String> teamNames = new ArrayList<>();
        client.getTeamsForUser(schema, username, t -> {
            teamNames.add(t.name);

            return true;
        });

        return teamNames;
    }
}
