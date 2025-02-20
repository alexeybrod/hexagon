package com.hexagonkt.http.client

import com.hexagonkt.logging.logger
import com.hexagonkt.helpers.require
import com.hexagonkt.http.Method.GET
import com.hexagonkt.http.Protocol.HTTPS
import com.hexagonkt.http.SslSettings
import com.hexagonkt.http.server.Call
import com.hexagonkt.http.server.Server
import com.hexagonkt.http.server.ServerSettings
import com.hexagonkt.http.server.jetty.JettyServletAdapter
import com.hexagonkt.http.server.serve
import com.hexagonkt.serialization.*
import org.junit.jupiter.api.*

import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.File
import java.net.URL
import kotlin.test.assertEquals

@TestInstance(PER_CLASS)
abstract class ClientTest(private val adapter: () -> ClientPort) {

    private var handler: Call.() -> Unit = {}

    private val server: Server by lazy {
        Server(JettyServletAdapter()) {
            post("/*") { handler() }
            get("/*") { handler() }
            head("/*") { handler() }
            put("/*") { handler() }
            delete("/*") { handler() }
            trace("/*") { handler() }
            options("/*") { handler() }
            patch("/*") { handler() }
        }
    }

    init {
        SerializationManager.formats = linkedSetOf(Json, Yaml)
    }

    private val client by lazy {
        Client(adapter(), "http://localhost:${server.runtimePort}", ClientSettings(Json))
    }

    @BeforeAll fun startup() {
        server.start()
    }

    @AfterAll fun shutdown() {
        server.stop()
    }

    @BeforeEach fun resetHandler() {
        handler = {
            response.headers["content-type"] = "application/json;charset=utf-8"
            response.headers["body"] = request.body
            ok(request.body)
        }
    }

    @Test fun `Create HTTP clients`() {
        val adapter = adapter()

        // clientCreation
        Client(adapter)
        Client(adapter, "http://host:1234/base")
        // clientCreation

        // clientSettingsCreation
        // All client settings parameters are optionals and provide default values
        Client(adapter, "http://host:1234/base", ClientSettings(
            contentType = "application/json",
            useCookies = true,
            headers = mapOf("X-Api-Key" to listOf("cafebabe")), // Headers to use in all requests
            user = "user",                                      // HTTP Basic auth user
            password = "password",                              // HTTP Basic auth password
            insecure = false,               // If true, the client doesn't check server certificates
            sslSettings = SslSettings()     // Key stores settings (check TLS section for details)
        ))
        // clientSettingsCreation
    }

    @Test fun `JSON requests works as expected`() {
        val expectedBody = "{  \"foo\" : \"fighters\",  \"es\" : \"áéíóúÁÉÍÓÚñÑ\"}"
        val requestBody = mapOf("foo" to "fighters", "es" to "áéíóúÁÉÍÓÚñÑ")

        val body = client.post("/", requestBody, Json.contentType).body
        assertEquals(expectedBody, body.toString().trim().replace("[\r\n]".toRegex(), ""))

        val body2 = client.post("/", body = requestBody).body
        assertEquals(expectedBody, body2.toString().trim().replace("[\r\n]".toRegex(), ""))

        client.get("/")
        client.get("/")
    }

    @Test fun `HTTP generic requests work ok`() {

        // genericRequest
        val request = Request(
            method = GET,
            path = "/",
            body = mapOf("body" to "payload"),
            headers = mapOf("X-Header" to listOf("value")),
            contentType = Json.contentType
        )

        val response = client.send(request)
        // genericRequest

        checkResponse(response, mapOf("body" to "payload"))
    }

    @Test fun `HTTP methods without body work ok`() {

        // withoutBodyRequests
        val responseGet = client.get("/")
        val responseHead = client.head("/")
        val responsePost = client.post("/")
        val responsePut = client.put("/")
        val responseDelete = client.delete("/")
        val responseTrace = client.trace("/")
        val responseOptions = client.options("/")
        val responsePatch = client.patch("/")
        // withoutBodyRequests

        checkResponse(responseGet, null)
        checkResponse(responseHead, null)
        checkResponse(responsePost, null)
        checkResponse(responsePut, null)
        checkResponse(responseDelete, null)
        checkResponse(responseTrace, null)
        checkResponse(responseOptions, null)
        checkResponse(responsePatch, null)
    }

    @Test fun `HTTP methods with body work ok`() {

        // bodyRequests
        val body = mapOf("key" to "value")

        val responseGet = client.get("/", body = body)
        val responsePost = client.post("/", body)
        val responsePut = client.put("/", body)
        val responseDelete = client.delete("/", body)
        val responseTrace = client.trace("/", body)
        val responseOptions = client.options("/", body)
        val responsePatch = client.patch("/", body)
        // bodyRequests

        checkResponse(responseGet, body)
        checkResponse(responsePost, body)
        checkResponse(responsePut, body)
        checkResponse(responseDelete, body)
        checkResponse(responseTrace, body)
        checkResponse(responseOptions, body)
        checkResponse(responsePatch, body)
    }

    @Test fun `HTTP methods with body and content type work ok`() {

        // bodyAndContentTypeRequests
        val body = mapOf("key" to "value")

        val responseGet = client.get("/", body = body, format = Yaml)
        val responsePost = client.post("/", body, Yaml)
        val responsePut = client.put("/", body, Yaml)
        val responseDelete = client.delete("/", body, Yaml)
        val responseTrace = client.trace("/", body, Yaml)
        val responseOptions = client.options("/", body, Yaml)
        val responsePatch = client.patch("/", body, Yaml)
        // bodyAndContentTypeRequests

        checkResponse(responseGet, body, Yaml)
        checkResponse(responsePost, body, Yaml)
        checkResponse(responsePut, body, Yaml)
        checkResponse(responseDelete, body, Yaml)
        checkResponse(responseTrace, body, Yaml)
        checkResponse(responseOptions, body, Yaml)
        checkResponse(responsePatch, body, Yaml)
    }

    @Test fun `Parameters are set properly` () {
        val endpoint = "http://localhost:${server.runtimePort}"
        val h = mapOf("header1" to listOf("val1", "val2"))
        val settings = ClientSettings(Json.contentType, false, h, "user", "password", true)
        val c = Client(adapter(), endpoint, settings)

        assert(c.settings.contentType == Json.contentType)
        assert(!c.settings.useCookies)
        assert(c.settings.headers == h)

        handler = {
            response.headersValues["auth"] = listOf(request.headers.require("Authorization"))
            response.headersValues["head1"] = request.headersValues.require("header1")
        }

        c.get("/auth").apply {
            assert(headers["auth"]?.firstOrNull()?.startsWith("Basic") ?: false)
            assert(headers["head1"]?.contains("val1") ?: false)
            assert(headers["head1"]?.contains("val2") ?: false)
            assert(status == 200)
        }
    }

    @Test fun `Files are sent in base64` () {
        handler = { response.headersValues["file64"] = listOf(request.body) }

        val file = File("../hexagon_core/src/test/resources/logging.properties").let {
            if (it.exists()) it
            else File("hexagon_core/src/test/resources/logging.properties")
        }

        val r = client.post("/file", file)
        assert(r.headers.require("file64").isNotEmpty())
        assert(r.status == 200)
    }

    @Test fun `Integers are sent properly` () {
        var run: Boolean

        client.post("/string", 42).apply {
            assert(headers.require("body").isNotEmpty())
            assert(status == 200)
            run = true
        }

        assert(run)
    }

    @Test fun `Strings are sent properly` () {
        var run: Boolean

        client.post("/string", "text").apply {
            assert(headers["body"]?.isNotEmpty() ?: false)
            assert(status == 200)
            run = true
        }

        assert(run)
    }

    @Test fun `Request HTTPS example`() {

        val serverAdapter = JettyServletAdapter()

        // Key store files
        val identity = "hexagonkt.p12"
        val trust = "trust.p12"

        // Default passwords are file name reversed
        val keyStorePassword = identity.reversed()
        val trustStorePassword = trust.reversed()

        // Key stores can be set as URIs to classpath resources (the triple slash is needed)
        val keyStore = URL("classpath:ssl/$identity")
        val trustStore = URL("classpath:ssl/$trust")

        val sslSettings = SslSettings(
            keyStore = keyStore,
            keyStorePassword = keyStorePassword,
            trustStore = trustStore,
            trustStorePassword = trustStorePassword,
            clientAuth = true // Requires a valid certificate from the client (mutual TLS)
        )

        val serverSettings = ServerSettings(
            bindPort = 0,
            protocol = HTTPS, // You can also use HTTP2
            sslSettings = sslSettings
        )

        val server = serve(serverSettings, serverAdapter) {
            get("/hello") {
                // We can access the certificate used by the client from the request
                val subjectDn = request.certificate?.subjectDN?.name
                response.headers["cert"] = subjectDn
                ok("Hello World!")
            }
        }

        // We'll use the same certificate for the client (in a real scenario it would be different)
        val clientSettings = ClientSettings(sslSettings = sslSettings)

        // Create a HTTP client and make a HTTPS request
        val client = Client(adapter(), "https://localhost:${server.runtimePort}", clientSettings)
        client.get("/hello").apply {
            logger.debug { body }
            // Assure the certificate received (and returned) by the server is correct
            assert(headers.require("cert").first().startsWith("CN=hexagonkt.com"))
            assert(body == "Hello World!")
        }

        server.stop()
    }

    private fun checkResponse(
        response: Response, parameter: Map<String, String>?, format: SerializationFormat = Json) {

        assert(response.status == 200)
        assert(response.body?.trim() == parameter?.serialize(format)?.trim() ?: "")
    }
}
