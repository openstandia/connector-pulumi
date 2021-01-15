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
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PulumiAssociationHandler {

    private static final Log LOGGER = Log.getLog(PulumiAssociationHandler.class);

    private final PulumiConfiguration configuration;
    private final PulumiClient client;

    public PulumiAssociationHandler(PulumiConfiguration configuration, PulumiClient client) {
        this.configuration = configuration;
        this.client = client;
    }

    public void updateTeamMembers(Uid uid, List<Object> addUserEmail, List<Object> removeUserEmails) {
        if (isEmpty(addUserEmail) && isEmpty(removeUserEmails)) {
            return;
        }
        client.assignUsersToTeam(uid, toList(addUserEmail), toList(removeUserEmails));
    }

    public List<String> getUsersForTeam(PulumiClient.PulumiTeamWithMembersRepresentation team) {
        List<String> users = new ArrayList<>();
        getUsersForTeam(team, email -> {
            users.add(email);
            return true;
        });
        return users;
    }

    public void getUsersForTeam(PulumiClient.PulumiTeamWithMembersRepresentation team, PulumiQueryHandler<String> handler) {
        client.getUsersForTeam(team, handler);
    }

    // Utilities

    private <T> Stream<T> streamNullable(Collection<T> list) {
        if (list == null) {
            return Stream.empty();
        }
        return list.stream();
    }

    private List<String> toList(Collection<?> list) {
        return streamNullable(list).map(x -> x.toString()).collect(Collectors.toList());
    }

    private boolean isEmpty(List<Object> list) {
        return list == null || list.isEmpty();
    }

}
