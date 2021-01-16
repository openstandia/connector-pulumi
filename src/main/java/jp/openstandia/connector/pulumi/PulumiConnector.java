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

import jp.openstandia.connector.pulumi.rest.PulumiRESTClient;
import okhttp3.*;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.InstanceNameAware;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static jp.openstandia.connector.pulumi.PulumiTeamHandler.TEAM_OBJECT_CLASS;
import static jp.openstandia.connector.pulumi.PulumiUserHandler.USER_OBJECT_CLASS;

@ConnectorClass(configurationClass = PulumiConfiguration.class, displayNameKey = "NRI OpenStandia Pulumi Connector")
public class PulumiConnector implements PoolableConnector, CreateOp, UpdateDeltaOp, DeleteOp, SchemaOp, TestOp, SearchOp<PulumiFilter>, InstanceNameAware {

    private static final Log LOG = Log.getLog(PulumiConnector.class);

    protected PulumiConfiguration configuration;
    protected PulumiClient client;

    private Map<String, AttributeInfo> userSchemaMap;
    private String instanceName;

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(Configuration configuration) {
        this.configuration = (PulumiConfiguration) configuration;

        try {
            authenticateResource();
        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }

        LOG.ok("Connector {0} successfully initialized", getClass().getName());
    }

    protected void authenticateResource() {
        OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder();
        okHttpBuilder.connectTimeout(configuration.getConnectTimeoutInMilliseconds(), TimeUnit.MILLISECONDS);
        okHttpBuilder.readTimeout(configuration.getReadTimeoutInMilliseconds(), TimeUnit.MILLISECONDS);
        okHttpBuilder.writeTimeout(configuration.getWriteTimeoutInMilliseconds(), TimeUnit.MILLISECONDS);
        okHttpBuilder.addInterceptor(getInterceptor(configuration.getAccessToken()));

        // Setup http proxy aware httpClient
        if (StringUtil.isNotEmpty(configuration.getHttpProxyHost())) {
            okHttpBuilder.proxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(configuration.getHttpProxyHost(), configuration.getHttpProxyPort())));

            if (StringUtil.isNotEmpty(configuration.getHttpProxyUser()) && configuration.getHttpProxyPassword() != null) {
                configuration.getHttpProxyPassword().access(c -> {
                    okHttpBuilder.proxyAuthenticator((Route route, Response response) -> {
                        String credential = Credentials.basic(configuration.getHttpProxyUser(), String.valueOf(c));
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    });
                });
            }
        }

        OkHttpClient httpClient = okHttpBuilder.build();

        client = new PulumiRESTClient(instanceName, configuration, httpClient);

        // Verify we can access pulumi API
        client.test();
    }

    private Interceptor getInterceptor(GuardedString accessToken) {
        return new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request.Builder builder = chain.request().newBuilder()
                        .addHeader("Accept", "application/vnd.pulumi+4");
                accessToken.access(c -> {
                    builder.addHeader("Authorization", "token " + String.valueOf(c));
                });
                return chain.proceed(builder.build());
            }
        };
    }

    @Override
    public Schema schema() {
        try {
            SchemaBuilder schemaBuilder = new SchemaBuilder(PulumiConnector.class);

            ObjectClassInfo userSchemaInfo = PulumiUserHandler.createSchema();
            schemaBuilder.defineObjectClass(userSchemaInfo);

            ObjectClassInfo teamSchemaInfo = PulumiTeamHandler.createSchema();
            schemaBuilder.defineObjectClass(teamSchemaInfo);

            schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildAttributesToGet(), SearchOp.class);
            schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildReturnDefaultAttributes(), SearchOp.class);

            userSchemaMap = new HashMap<>();
            userSchemaInfo.getAttributeInfo().stream()
                    .forEach(a -> userSchemaMap.put(a.getName(), a));
            userSchemaMap.put(Uid.NAME, AttributeInfoBuilder.define("username").build());
            userSchemaMap = Collections.unmodifiableMap(userSchemaMap);

            return schemaBuilder.build();

        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    private Map<String, AttributeInfo> getUserSchemaMap() {
        // Load schema map if it's not loaded yet
        if (userSchemaMap == null) {
            schema();
        }
        return userSchemaMap;
    }

    protected PulumiObjectHandler createPulumiObjectHandler(ObjectClass objectClass) {
        if (objectClass == null) {
            throw new InvalidAttributeValueException("ObjectClass value not provided");
        }

        if (objectClass.equals(USER_OBJECT_CLASS)) {
            return new PulumiUserHandler(configuration, client, getUserSchemaMap());

        } else if (objectClass.equals(TEAM_OBJECT_CLASS)) {
            return new PulumiTeamHandler(configuration, client);

        } else {
            throw new InvalidAttributeValueException("Unsupported object class " + objectClass);
        }
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        if (createAttributes == null || createAttributes.isEmpty()) {
            throw new InvalidAttributeValueException("Attributes not provided or empty");
        }

        try {
            return createPulumiObjectHandler(objectClass).create(createAttributes);

        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objectClass, Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        if (uid == null) {
            throw new InvalidAttributeValueException("uid not provided");
        }

        try {
            return createPulumiObjectHandler(objectClass).updateDelta(uid, modifications, options);

        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        if (uid == null) {
            throw new InvalidAttributeValueException("uid not provided");
        }

        try {
            createPulumiObjectHandler(objectClass).delete(uid, options);

        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    @Override
    public FilterTranslator<PulumiFilter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return new PulumiFilterTranslator(objectClass, options);
    }

    @Override
    public void executeQuery(ObjectClass objectClass, PulumiFilter filter, ResultsHandler resultsHandler, OperationOptions options) {
        createPulumiObjectHandler(objectClass).query(filter, resultsHandler, options);
    }

    @Override
    public void test() {
        try {
            dispose();
            authenticateResource();
        } catch (RuntimeException e) {
            throw processRuntimeException(e);
        }
    }

    @Override
    public void dispose() {
        client.close();
        this.client = null;
    }

    @Override
    public void checkAlive() {
        // Do nothing
    }

    @Override
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    protected ConnectorException processRuntimeException(RuntimeException e) {
        if (e instanceof ConnectorException) {
            // Write error log because IDM might not write full stack trace
            // It's hard to debug the error
            if (e instanceof AlreadyExistsException) {
                LOG.warn(e, "Detect pulumi connector error");

            } else if (e instanceof UnknownUidException) {
                LOG.warn(e, "Detect pulumi connector error");

            } else {
                LOG.error(e, "Detect pulumi connector error");
            }
            return (ConnectorException) e;
        }
        LOG.error(e, "Detect pulumi connector error");

        return new ConnectorIOException(e);
    }
}
