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

import org.identityconnectors.framework.common.objects.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides utility methods
 *
 * @author Hiroyuki Wada
 */
public class PulumiUtils {

    public static String toResourceValue(Attribute attr) {
        String value = AttributeUtil.getStringValue(attr);
        return toResourceValue(value);
    }

    public static String toResourceValue(AttributeDelta delta) {
        String value = AttributeDeltaUtil.getStringValue(delta);
        return toResourceValue(value);
    }

    public static String toResourceValue(String value) {
        if (value == null) {
            // To delete value, return empty string
            return "";
        }

        return value;
    }

    /**
     * Check if attrsToGetSet contains the attribute.
     *
     * @param attrsToGetSet
     * @param attr
     * @return
     */
    public static boolean shouldReturn(Set<String> attrsToGetSet, String attr) {
        if (attrsToGetSet == null) {
            return true;
        }
        return attrsToGetSet.contains(attr);
    }

    /**
     * Check if ALLOW_PARTIAL_ATTRIBUTE_VALUES == true.
     *
     * @param options
     * @return
     */
    public static boolean shouldAllowPartialAttributeValues(OperationOptions options) {
        // If the option isn't set from IDM, it may be null.
        return Boolean.TRUE.equals(options.getAllowPartialAttributeValues());
    }

    /**
     * Check if RETURN_DEFAULT_ATTRIBUTES == true.
     *
     * @param options
     * @return
     */
    public static boolean shouldReturnDefaultAttributes(OperationOptions options) {
        // If the option isn't set from IDM, it may be null.
        return Boolean.TRUE.equals(options.getReturnDefaultAttributes());
    }

    /**
     * Create full set of ATTRIBUTES_TO_GET which is composed by RETURN_DEFAULT_ATTRIBUTES + ATTRIBUTES_TO_GET.
     *
     * @param schema
     * @param options
     * @return
     */
    public static Set<String> createFullAttributesToGet(Map<String, AttributeInfo> schema, OperationOptions options) {
        Set<String> attributesToGet = null;
        if (shouldReturnDefaultAttributes(options)) {
            attributesToGet = new HashSet<>();
            attributesToGet.addAll(toReturnedByDefaultAttributesSet(schema));
        }
        if (options.getAttributesToGet() != null) {
            if (attributesToGet == null) {
                attributesToGet = new HashSet<>();
            }
            for (String a : options.getAttributesToGet()) {
                attributesToGet.add(a);
            }
        }
        return attributesToGet;
    }

    private static Set<String> toReturnedByDefaultAttributesSet(Map<String, AttributeInfo> schema) {
        return schema.entrySet().stream()
                .filter(entry -> entry.getValue().isReturnedByDefault())
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());
    }
}
