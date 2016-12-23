Steps to create sample certificate are:

1. Generate key.

keytool -genkey -alias qdsample -keyalg RSA -keysize 4096 -validity 3650 -keystore qdkeystore -keypass qdsample -storepass qdsample -dname "CN=QDSample"

2. Examine store.

keytool -list -v -keystore qdkeystore -storepass qdsample

3. Export self-signed certificate (should have "Entry type: keyEntry")

keytool -export -alias qdsample -keystore qdkeystore -storepass qdsample -file qdsample.cer -rfc

4. Import the certificate into a new truststore.

keytool -import -noprompt -alias qdsample -file qdsample.cer -keystore qdtruststore -storepass qdsample

5. Examine the truststore (should have "Entry type: trustedCertEntry")

keytool -list -v -keystore qdtruststore -storepass qdsample


---

Now, server shall set the following system properties:

-Djavax.net.ssl.keyStore=qdkeystore
-Djavax.net.ssl.keyStorePassword=qdsample

And client shall set the following system properties:

-Djavax.net.ssl.trustStore=qdtruststore
-Djavax.net.ssl.trustStorePassword=qdsample
