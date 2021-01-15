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

import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PulumiUtilsTest {

    @Test
    void shouldReturnPartialAttributeValues() {
        OperationOptions noOptions = new OperationOptionsBuilder().build();
        assertFalse(PulumiUtils.shouldAllowPartialAttributeValues(noOptions));

        OperationOptions falseOption = new OperationOptionsBuilder().setAllowPartialAttributeValues(false).build();
        assertFalse(PulumiUtils.shouldAllowPartialAttributeValues(falseOption));

        OperationOptions trueOption = new OperationOptionsBuilder().setAllowPartialAttributeValues(true).build();
        assertTrue(PulumiUtils.shouldAllowPartialAttributeValues(trueOption));
    }
}