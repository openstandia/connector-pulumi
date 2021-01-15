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

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class PulumiConfiguration extends AbstractConfiguration {

    private GuardedString accessToken;
    private String organization;
    private int connectTimeoutInMilliseconds = 10000; // 10s
    private int readTimeoutInMilliseconds = 30000; // 30s
    private int writeTimeoutInMilliseconds = 30000; // 30s

    private String httpProxyHost;
    private int httpProxyPort;
    private String httpProxyUser;
    private GuardedString httpProxyPassword;

    /**
     * Return base API URL for inivitation.
     *
     * @return
     */
    public String getPulumiConsoleURL() {
        return "https://api.pulumi.com/api/console/orgs/" + this.organization;
    }

    /**
     * Return base API URL.
     *
     * @return
     */
    public String getPulumiURL() {
        return "https://api.pulumi.com/api/orgs/" + this.organization;
    }

    public String getPulumiSelfURL() {
        return "https://api.pulumi.com/api/user";
    }

    @ConfigurationProperty(
            order = 1,
            displayMessageKey = "Pulumi Access Token",
            helpMessageKey = "Access token for Pulumi REST API.",
            required = true,
            confidential = true)
    public GuardedString getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(GuardedString accessToken) {
        this.accessToken = accessToken;
    }

    @ConfigurationProperty(
            order = 2,
            displayMessageKey = "Pulumi Organization",
            helpMessageKey = "Organization name for Pulumi REST API.",
            required = true,
            confidential = false)
    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    @ConfigurationProperty(
            order = 3,
            displayMessageKey = "Connect Timeout (milliseconds)",
            helpMessageKey = "Connect timeout in milliseconds. (Default: 10000)",
            required = false,
            confidential = false)
    public int getConnectTimeoutInMilliseconds() {
        return connectTimeoutInMilliseconds;
    }

    public void setConnectTimeoutInMilliseconds(int connectTimeoutInMilliseconds) {
        this.connectTimeoutInMilliseconds = connectTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 4,
            displayMessageKey = "Read Timeout (milliseconds)",
            helpMessageKey = "Read timeout in milliseconds. (Default: 30000)",
            required = false,
            confidential = false)
    public int getReadTimeoutInMilliseconds() {
        return readTimeoutInMilliseconds;
    }

    public void setReadTimeoutInMilliseconds(int readTimeoutInMilliseconds) {
        this.readTimeoutInMilliseconds = readTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 5,
            displayMessageKey = "Write Timeout (milliseconds)",
            helpMessageKey = "Write timeout in milliseconds. (Default: 30000)",
            required = false,
            confidential = false)
    public int getWriteTimeoutInMilliseconds() {
        return writeTimeoutInMilliseconds;
    }

    public void setWriteTimeoutInMilliseconds(int writeTimeoutInMilliseconds) {
        this.writeTimeoutInMilliseconds = writeTimeoutInMilliseconds;
    }

    @ConfigurationProperty(
            order = 6,
            displayMessageKey = "HTTP Proxy Host",
            helpMessageKey = "Hostname for the HTTP Proxy",
            required = false,
            confidential = false)
    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    @ConfigurationProperty(
            order = 7,
            displayMessageKey = "HTTP Proxy Port",
            helpMessageKey = "Port for the HTTP Proxy",
            required = false,
            confidential = false)
    public int getHttpProxyPort() {
        return httpProxyPort;
    }

    public void setHttpProxyPort(int httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    @ConfigurationProperty(
            order = 8,
            displayMessageKey = "HTTP Proxy User",
            helpMessageKey = "Username for the HTTP Proxy Authentication",
            required = false,
            confidential = false)
    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    @ConfigurationProperty(
            order = 9,
            displayMessageKey = "HTTP Proxy Password",
            helpMessageKey = "Password for the HTTP Proxy Authentication",
            required = false,
            confidential = true)
    public GuardedString getHttpProxyPassword() {
        return httpProxyPassword;
    }

    public void setHttpProxyPassword(GuardedString httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    @Override
    public void validate() {
    }
}
