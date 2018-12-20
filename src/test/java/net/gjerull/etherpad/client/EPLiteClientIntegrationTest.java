package net.gjerull.etherpad.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.StringBody;

import etm.core.configuration.BasicEtmConfigurator;
import etm.core.configuration.EtmManager;
import etm.core.monitor.EtmMonitor;
import etm.core.renderer.SimpleTextRenderer;

/**
 * Integration test for simple App.
 */
public class EPLiteClientIntegrationTest {
	private EPLiteClient client;
	private ClientAndServer mockServer;
	private EtmMonitor monitor;

	/**
	 * Useless testing as it depends on a specific API key
	 *
	 * TODO: Find a way to make it configurable
	 */
	@Before
	public void setUp() throws Exception {
		this.client = new EPLiteClient("http://localhost:9001",
				"a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58");
		mockServer = startClientAndServer(9001);
		// To turn off display of mock server logs
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.mockserver.mock"))
				.setLevel(ch.qos.logback.classic.Level.OFF);

		BasicEtmConfigurator.configure();
		monitor = EtmManager.getEtmMonitor();
		monitor.start();
	}

	@After
	public void tearDown() {
		mockServer.stop();
		monitor.render(new SimpleTextRenderer());
		monitor.stop();
	}

	@Test
	public void validate_token() throws Exception {
		mockServer
				.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/checkToken")
						.withBody("{\"apikey\":\"a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58\"}"))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.checkToken();
	}

	@Test
	public void create_and_delete_group() throws Exception {
		mockServer
				.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createGroup").withBody(
						new StringBody("apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"groupID\":\"g.s8oes9dhwrvt0zif\"}}"));

		Map response = client.createGroup();
		assertTrue(response.containsKey("groupID"));
		String groupId = (String) response.get("groupID");
		assertTrue("Unexpected groupID " + groupId, groupId != null && groupId.startsWith("g."));

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/deleteGroup")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&groupID=g.s8oes9dhwrvt0zif")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.deleteGroup(groupId);
	}

	@Test
	public void create_group_if_not_exists_for_and_list_all_groups() throws Exception {
		String groupMapper = "groupname";

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createGroupIfNotExistsFor")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&groupMapper=groupname")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"groupID\":\"g.s8oes9dhwrvt0zif\"}}"));

		Map response = client.createGroupIfNotExistsFor(groupMapper);

		assertTrue(response.containsKey("groupID"));
		String groupId = (String) response.get("groupID");
		try {

			mockServer
					.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/listAllGroups").withBody(
							new StringBody("apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58")))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"groupIDs\":[\"g.s8oes9dhwrvt0zif\"]}}"));

			Map listResponse = client.listAllGroups();
			assertTrue(listResponse.containsKey("groupIDs"));
			int firstNumGroups = ((List) listResponse.get("groupIDs")).size();

			client.createGroupIfNotExistsFor(groupMapper);

			listResponse = client.listAllGroups();
			int secondNumGroups = ((List) listResponse.get("groupIDs")).size();

			assertEquals(firstNumGroups, secondNumGroups);
		} finally {

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/deleteGroup")
					.withBody(new StringBody(
							"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&groupID=g.s8oes9dhwrvt0zif")))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.deleteGroup(groupId);
		}
	}

	@Test
	public void create_group_pads_and_list_them() throws Exception {
		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createGroup"))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"groupID\":\"g.s8oes9dhwrvt0zif\"}}"));

		Map response = client.createGroup();

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createGroupPad"))
				.respond(HttpResponse.response().withStatusCode(200).withBody(
						"{\"code\":0,\"message\":\"ok\",\"data\":{\"padID\":\"g.s8oes9dhwrvt0zif$integration-test-1\"}}"));

		String groupId = (String) response.get("groupID");
		String pad1 = "integration-test-1";
		String pad2 = "integration-test-2";

		try {

			Map padResponse = client.createGroupPad(groupId, pad1);
			assertTrue(padResponse.containsKey("padID"));
			String padId1 = (String) padResponse.get("padID");

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/setPublicStatus"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getPublicStatus"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"publicStatus\":true}}"));

			client.setPublicStatus(padId1, true);
			boolean publicStatus = (boolean) client.getPublicStatus(padId1).get("publicStatus");
			assertTrue(publicStatus);

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/setPassword"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/isPasswordProtected"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"isPasswordProtected\":true}}"));

			client.setPassword(padId1, "integration");
			boolean passwordProtected = (boolean) client.isPasswordProtected(padId1).get("isPasswordProtected");
			assertTrue(passwordProtected);

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createGroupPad"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"padID\":\"g.s8oes9dhwrvt0zif$integration-test-2\"}}"));

			padResponse = client.createGroupPad(groupId, pad2, "Initial text");
			assertTrue(padResponse.containsKey("padID"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getText"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"text\":\"Initial text\\n\"}}"));

			String padId = (String) padResponse.get("padID");
			String initialText = (String) client.getText(padId).get("text");
			assertEquals("Initial text\n", initialText);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/listPads"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"padIDs\":[\"g.12$integration-test-1\",\"g.12$integration-test-2\"]}}"));

			Map padListResponse = client.listPads(groupId);
			assertTrue(padListResponse.containsKey("padIDs"));
			List padIds = (List) padListResponse.get("padIDs");
			assertEquals(2, padIds.size());

		} finally {

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/deleteGroup"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.deleteGroup(groupId);
		}
	}

	@Test
	public void create_author() throws Exception {

		mockServer
				.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/createAuthor").withBody(
						new StringBody("apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorID\":\"a.s8oes9dhwrvt0zif\"}}"));

		Map authorResponse = client.createAuthor();
		String authorId = (String) authorResponse.get("authorID");
		assertTrue(authorId != null && !authorId.isEmpty());

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createAuthor")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&name=integration-author")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorID\":\"a.s8oes9dhwrvt0zif\"}}"));

		authorResponse = client.createAuthor("integration-author");
		authorId = (String) authorResponse.get("authorID");
		mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getAuthorName")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&authorID=a.s8oes9dhwrvt0zif")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":\"integration-author\"}"));

		String authorName = client.getAuthorName(authorId);
		assertEquals("integration-author", authorName);
	}

	@Test
	public void create_author_with_author_mapper() throws Exception {
		String authorMapper = "username";
		HttpRequest request = HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getAuthorName")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&authorID=a.s8oes9dhwrvt0zif"));

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createAuthorIfNotExistsFor")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&name=integration-author-1&authorMapper=username")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorID\":\"a.s8oes9dhwrvt0zif\"}}"));

		Map authorResponse = client.createAuthorIfNotExistsFor(authorMapper, "integration-author-1");
		String firstAuthorId = (String) authorResponse.get("authorID");
		assertTrue(firstAuthorId != null && !firstAuthorId.isEmpty());

		mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getAuthorName")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&authorID=a.s8oes9dhwrvt0zif")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":\"integration-author-1\"}"));

		String firstAuthorName = client.getAuthorName(firstAuthorId);

		mockServer.clear(request);

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createAuthorIfNotExistsFor")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&name=integration-author-2&authorMapper=username")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorID\":\"a.s8oes9dhwrvt0zif\"}}"));

		authorResponse = client.createAuthorIfNotExistsFor(authorMapper, "integration-author-2");
		String secondAuthorId = (String) authorResponse.get("authorID");
		assertEquals(firstAuthorId, secondAuthorId);

		mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getAuthorName")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&authorID=a.s8oes9dhwrvt0zif")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":\"integration-author-2\"}"));

		String secondAuthorName = client.getAuthorName(secondAuthorId);

		assertNotEquals(firstAuthorName, secondAuthorName);

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createAuthorIfNotExistsFor")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&authorMapper=username")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorID\":\"a.s8oes9dhwrvt0zif\"}}"));

		authorResponse = client.createAuthorIfNotExistsFor(authorMapper);
		String thirdAuthorId = (String) authorResponse.get("authorID");
		assertEquals(secondAuthorId, thirdAuthorId);

		mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getAuthorName")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&authorID=a.s8oes9dhwrvt0zif")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":\"integration-author-3\"}"));

		String thirdAuthorName = client.getAuthorName(thirdAuthorId);

		assertEquals(secondAuthorName, thirdAuthorName);
	}

	@Test
	public void create_and_delete_session() throws Exception {
		String authorMapper = "username";
		String groupMapper = "groupname";

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createGroupIfNotExistsFor")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&groupMapper=groupname")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"groupID\":\"g.s8oes9dhwrvt0zif\"}}"));

		Map groupResponse = client.createGroupIfNotExistsFor(groupMapper);
		String groupId = (String) groupResponse.get("groupID");

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createAuthorIfNotExistsFor")
				.withBody(new StringBody(
						"apikey=a04f17343b51afaa036a7428171dd873469cd85911ab43be0503d29d2acbbd58&name=integration-author-1&authorMapper=username")))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorID\":\"a.s8oes9dhwrvt0zif\"}}"));

		Map authorResponse = client.createAuthorIfNotExistsFor(authorMapper, "integration-author-1");
		String authorId = (String) authorResponse.get("authorID");

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createSession"))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"sessionID\":\"s.s8oes9dhwrvt0zif\"}}"));

		int sessionDuration = 8;
		Map sessionResponse = client.createSession(groupId, authorId, sessionDuration);
		String firstSessionId = (String) sessionResponse.get("sessionID");

		Calendar oneYearFromNow = Calendar.getInstance();
		oneYearFromNow.add(Calendar.YEAR, 1);
		Date sessionValidUntil = oneYearFromNow.getTime();

		mockServer.clear(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createSession"));

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createSession"))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"sessionID\":\"s.s8oes9dhwrvt0z02\"}}"));

		sessionResponse = client.createSession(groupId, authorId, sessionValidUntil);
		String secondSessionId = (String) sessionResponse.get("sessionID");
		try {
			assertNotEquals(firstSessionId, secondSessionId);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getSessionInfo"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"groupID\":\"g.s8oes9dhwrvt0zif\",\"authorID\":\"a.s8oes9dhwrvt0zif\",\"validUntil\":"
									+ Long.toString(sessionValidUntil.getTime() / 1000L) + "}}"));

			Map sessionInfo = client.getSessionInfo(secondSessionId);
			assertEquals(groupId, sessionInfo.get("groupID"));
			assertEquals(authorId, sessionInfo.get("authorID"));
			assertEquals(sessionValidUntil.getTime() / 1000L, (long) sessionInfo.get("validUntil"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/listSessionsOfGroup"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"s.s8oes9dhwrvt0zif\":{\"groupID\":\"g.s8oes9dhwrvt0zif\",\"authorID\":\"a.s8oes9dhwrvt0zif\",\"validUntil\":1542420364},\"s.s8oes9dhwrvt0z02\":{\"groupID\":\"g.s8oes9dhwrvt0zif\",\"authorID\":\"a.s8oes9dhwrvt0zif\",\"validUntil\":1573927564}}}"));

			Map sessionsOfGroup = client.listSessionsOfGroup(groupId);
			sessionInfo = (Map) sessionsOfGroup.get(firstSessionId);
			assertEquals(groupId, sessionInfo.get("groupID"));
			sessionInfo = (Map) sessionsOfGroup.get(secondSessionId);
			assertEquals(groupId, sessionInfo.get("groupID"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/listSessionsOfAuthor"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"s.s8oes9dhwrvt0zif\":{\"groupID\":\"g.s8oes9dhwrvt0zif\",\"authorID\":\"a.s8oes9dhwrvt0zif\",\"validUntil\":1542420364},\"s.s8oes9dhwrvt0z02\":{\"groupID\":\"g.s8oes9dhwrvt0zif\",\"authorID\":\"a.s8oes9dhwrvt0zif\",\"validUntil\":1573927564}}}"));

			Map sessionsOfAuthor = client.listSessionsOfAuthor(authorId);
			sessionInfo = (Map) sessionsOfAuthor.get(firstSessionId);
			assertEquals(authorId, sessionInfo.get("authorID"));
			sessionInfo = (Map) sessionsOfAuthor.get(secondSessionId);
			assertEquals(authorId, sessionInfo.get("authorID"));
		} finally {
			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/deleteSession"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.deleteSession(firstSessionId);
			client.deleteSession(secondSessionId);
		}

	}

	@Test
	public void create_pad_set_and_get_content() {
		String padID = "integration-test-pad";

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createPad")).respond(
				HttpResponse.response().withStatusCode(200).withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.createPad(padID);

		try {

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/setText"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.setText(padID, "integration test text");

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getText"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"text\":\"integration test text\"}}"));

			String text = (String) client.getText(padID).get("text");

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/setHTML"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.setHTML(padID, "<!DOCTYPE HTML><html><body><p>integration test</p></body></html>");

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getHTML"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"html\":\"<!DOCTYPE HTML><html><body><p>integration<br><br></p></body></html>\"}}"));

			String html = (String) client.getHTML(padID).get("html");
			assertTrue(html, html.contains("integration<br><br>"));

			mockServer.clear(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getHTML"));
			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getHTML"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"html\":\"<!DOCTYPE HTML><html><body><br></body></html>\"}}"));

			html = (String) client.getHTML(padID, 2).get("html");
			assertEquals("<!DOCTYPE HTML><html><body><br></body></html>", html);

			mockServer.clear(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getText"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getText"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"text\":\"\\n\"}}"));

			text = (String) client.getText(padID, 2).get("text");
			assertEquals("\n", text);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getRevisionsCount"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"revisions\":3}}"));

			long revisionCount = (long) client.getRevisionsCount(padID).get("revisions");
			assertEquals(3L, revisionCount);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getRevisionChangeset"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":\"Z:1>r|1+r$ integration test\\n\"}"));

			String revisionChangeset = client.getRevisionChangeset(padID);

			mockServer.clear(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getRevisionChangeset"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getRevisionChangeset"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":\"Z:j<i|1-j|1+1$\\n\"}"));

			revisionChangeset = client.getRevisionChangeset(padID, 2);
			assertTrue(revisionChangeset, revisionChangeset.contains("|1-j|1+1$\n"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/createDiffHTML"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"html\":\"<style>\\n.removed {text-decoration: line-through; -ms-filter:'progid:DXImageTransform.Microsoft.Alpha(Opacity=80)'; filter: alpha(opacity=80); opacity: 0.8; }\\n</style><span class=\\\"removed\\\">integration test</span><br><br>\",\"authors\":[\"\"]}}"));

			String diffHTML = (String) client.createDiffHTML(padID, 1, 2).get("html");
			assertTrue(diffHTML, diffHTML.contains("<span class=\"removed\">integration test</span>"));

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/appendText"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.appendText(padID, "integration test text 2");
			text = (String) client.getText(padID).get("text");

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getAttributePool"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"pool\":{\"numToAttrib\":{\"0\":[\"author\",\"\"],\"1\":[\"removed\",\"true\"]},\"attribToNum\":{\"author,\":0,\"removed,true\":1},\"nextNum\":2}}}"));

			Map attributePool = (Map) client.getAttributePool(padID).get("pool");
			assertTrue(attributePool.containsKey("attribToNum"));
			assertTrue(attributePool.containsKey("nextNum"));
			assertTrue(attributePool.containsKey("numToAttrib"));

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/saveRevision"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.saveRevision(padID);
			client.saveRevision(padID, 2);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getSavedRevisionsCount"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"savedRevisions\":2}}"));

			long savedRevisionCount = (long) client.getSavedRevisionsCount(padID).get("savedRevisions");
			assertEquals(2L, savedRevisionCount);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/listSavedRevisions"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"savedRevisions\":[2,4]}}"));

			List savedRevisions = (List) client.listSavedRevisions(padID).get("savedRevisions");
			assertEquals(2, savedRevisions.size());
			assertEquals(2L, savedRevisions.get(0));
			assertEquals(4L, savedRevisions.get(1));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/padUsersCount"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"padUsersCount\":0}}"));

			long padUsersCount = (long) client.padUsersCount(padID).get("padUsersCount");
			assertEquals(0, padUsersCount);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/padUsers"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"padUsers\":[]}}"));

			List padUsers = (List) client.padUsers(padID).get("padUsers");
			assertEquals(0, padUsers.size());

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getReadOnlyID"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"readOnlyID\":\"r.efb9156f0b6d471f721a3a6a7439cfd9\"}}"));

			String readOnlyId = (String) client.getReadOnlyID(padID).get("readOnlyID");

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getPadID"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"padID\":\"integration-test-pad\"}}"));

			String padIdFromROId = (String) client.getPadID(readOnlyId).get("padID");
			assertEquals(padID, padIdFromROId);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/listAuthorsOfPad"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorIDs\":[]}}"));

			List authorsOfPad = (List) client.listAuthorsOfPad(padID).get("authorIDs");
			assertEquals(0, authorsOfPad.size());

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getLastEdited"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"lastEdited\":1542395476864}}"));

			long lastEditedTimeStamp = (long) client.getLastEdited(padID).get("lastEdited");
			Calendar lastEdited = Calendar.getInstance();
			lastEdited.setTimeInMillis(lastEditedTimeStamp);
			Calendar now = Calendar.getInstance();
			assertTrue(lastEdited.before(now));

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/sendClientsMessage"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{}}"));

			client.sendClientsMessage(padID, "test message");

		} finally {

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/deletePad"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.deletePad(padID);
		}
	}

	@Test
	public void create_pad_move_and_copy() throws Exception {
		String keep = "keep";
		String change = "change";
		String padID = "integration-test-pad";
		String copyPadId = "integration-test-pad-copy";
		String movePadId = "integration-test-pad-move";

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createPad")).respond(
				HttpResponse.response().withStatusCode(200).withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.createPad(padID, keep);

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/copyPad"))
				.respond(HttpResponse.response().withStatusCode(200).withBody(
						"{\"code\":0,\"message\":\"ok\",\"data\":{\"padID\":\"integration-test-pad-copy\"}}"));

		client.copyPad(padID, copyPadId);

		mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getText"))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"text\":\"keep\\n\"}}"));

		String copyPadText = (String) client.getText(copyPadId).get("text");

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/movePad")).respond(
				HttpResponse.response().withStatusCode(200).withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.movePad(padID, movePadId);
		String movePadText = (String) client.getText(movePadId).get("text");

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/setText")).respond(
				HttpResponse.response().withStatusCode(200).withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.setText(movePadId, change);
		client.copyPad(movePadId, copyPadId, true);

		mockServer.clear(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getText"));

		mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getText"))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"text\":\"change\\n\"}}"));

		String copyPadTextForce = (String) client.getText(copyPadId).get("text");
		client.movePad(movePadId, copyPadId, true);
		String movePadTextForce = (String) client.getText(copyPadId).get("text");

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/deletePad")).respond(
				HttpResponse.response().withStatusCode(200).withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.deletePad(copyPadId);
		client.deletePad(padID);

		assertEquals(keep + "\n", copyPadText);
		assertEquals(keep + "\n", movePadText);

		assertEquals(change + "\n", copyPadTextForce);
		assertEquals(change + "\n", movePadTextForce);
	}

	@Test
	public void create_pads_and_list_them() throws InterruptedException {
		String pad1 = "integration-test-pad-1";
		String pad2 = "integration-test-pad-2";

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createPad")).respond(
				HttpResponse.response().withStatusCode(200).withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.createPad(pad1);
		client.createPad(pad2);

		Thread.sleep(100);

		mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/listAllPads"))
				.respond(HttpResponse.response().withStatusCode(200).withBody(
						"{\"code\":0,\"message\":\"ok\",\"data\":{\"padIDs\":[\"integration-test-pad-1\",\"integration-test-pad-2\"]}}"));

		List padIDs = (List) client.listAllPads().get("padIDs");

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/deletePad")).respond(
				HttpResponse.response().withStatusCode(200).withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.deletePad(pad1);
		client.deletePad(pad2);

		assertTrue(String.format("Size was %d", padIDs.size()), padIDs.size() >= 2);
		assertTrue(padIDs.contains(pad1));
		assertTrue(padIDs.contains(pad2));
	}

	@Test
	public void create_pad_and_chat_about_it() {
		String padID = "integration-test-pad-1";
		String user1 = "user1";
		String user2 = "user2";

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createAuthorIfNotExistsFor"))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorID\":\"a.s8oes9dhwrvt0zif\"}}"));

		Map response = client.createAuthorIfNotExistsFor(user1, "integration-author-1");
		String author1Id = (String) response.get("authorID");

		mockServer.clear(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createAuthorIfNotExistsFor"));

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createAuthorIfNotExistsFor"))
				.respond(HttpResponse.response().withStatusCode(200)
						.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"authorID\":\"a.s8oes9dhwrvt0z02\"}}"));

		response = client.createAuthorIfNotExistsFor(user2, "integration-author-2");
		String author2Id = (String) response.get("authorID");

		mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/createPad")).respond(
				HttpResponse.response().withStatusCode(200).withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

		client.createPad(padID);

		try {

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/appendChatMessage"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.appendChatMessage(padID, "hi from user1", author1Id);
			client.appendChatMessage(padID, "hi from user2", author2Id, System.currentTimeMillis() / 1000L);
			client.appendChatMessage(padID, "bye from user1", author1Id, System.currentTimeMillis() / 1000L);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getChatHead"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":{\"chatHead\":2}}"));

			response = client.getChatHead(padID);
			long chatHead = (long) response.get("chatHead");
			assertEquals(2, chatHead);

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getChatHistory"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"messages\": [ \"hi from user1\",\"hi from user2\",\"bye from user1\"]}}"));

			response = client.getChatHistory(padID);
			List chatHistory = (List) response.get("messages");
			assertEquals(3, chatHistory.size());

			mockServer.clear(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getChatHistory"));

			mockServer.when(HttpRequest.request().withMethod("GET").withPath("/api/1.2.13/getChatHistory"))
					.respond(HttpResponse.response().withStatusCode(200).withBody(
							"{\"code\":0,\"message\":\"ok\",\"data\":{\"messages\": [ {\"text\":\"hi from user1\"},{\"text\":\"hi from user2\"}]}}"));

			response = client.getChatHistory(padID, 0, 1);
			chatHistory = (List) response.get("messages");
			assertEquals(2, chatHistory.size());
			assertEquals("hi from user2", ((Map) chatHistory.get(1)).get("text"));
		} finally {

			mockServer.when(HttpRequest.request().withMethod("POST").withPath("/api/1.2.13/deletePad"))
					.respond(HttpResponse.response().withStatusCode(200)
							.withBody("{\"code\":0,\"message\":\"ok\",\"data\":null}"));

			client.deletePad(padID);
		}

	}
}