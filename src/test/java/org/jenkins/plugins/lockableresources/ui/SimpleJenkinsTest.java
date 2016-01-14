/*
 * The MIT License
 *
 * Copyright 2014-2015 Aki Asikainen.
 *                     SAP SE.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkins.plugins.lockableresources.ui;

import static org.junit.Assert.*;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.apache.commons.io.FileUtils;
import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourceProperty;
import org.jenkins.plugins.lockableresources.LockableResourceTestBase;
import org.jenkins.plugins.lockableresources.RequiredResourcesProperty;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.*;
import java.util.logging.Logger;

/**
 * Set of tests for job config and execution
 * NOTE: this set of tests take longer since it wakes clients
 *
 * @author jwillis
 */
public class SimpleJenkinsTest extends LockableResourceTestBase {

    private static final Logger LOGGER = Logger.getLogger(SimpleJenkinsTest.class.getName());
    public LockableResource instance = new LockableResource(generateUniqueID(), "r1", "d1", "l1 l2", "", null);

	public SimpleJenkinsTest() {
        super();
        j = new JenkinsRule();
	}

	@Before
	public void setUp() throws Exception {
        super.setUp();
        addTestResource(this.instance);
	}

	@After
	public void tearDown() throws Exception {
        super.tearDown();
        clearTestResources();
	}

	@Test
	public void testJobWithoutResources() throws Exception {
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildersList().add(createEchoStep("hello"));
		FreeStyleBuild build = project.scheduleBuild2(0).get();
        LOGGER.info(build.getDisplayName() + " completed");
		String s = FileUtils.readFileToString(build.getLogFile());
        LOGGER.info(s);
        assertTrue("Did not print out shell results in build.", s.contains("hello"));
	}

	@Test
	public void testJobConfigWithoutResources() throws Exception {
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildersList().add(createEchoStep("hello"));
		j.submit(j.createWebClient().getPage(project, "configure").getFormByName("config"));
		FreeStyleBuild build = project.scheduleBuild2(0).get();
        LOGGER.info(build.getDisplayName() + " completed");
		String s = FileUtils.readFileToString(build.getLogFile());
        LOGGER.info(s);
		assertTrue("Did not print out shell results in build.", s.contains("hello"));
	}

    @Test
	public void testJobConfigWithSingleRequestSingleResource() throws Exception {
        assertTrue("Not enough resources in the manager", manager.getResources().size() > 0);
		FreeStyleProject project = j.createFreeStyleProject();
		project.getBuildersList().add(createEnvDumpStep());

		// setup the list of resources to look for
		// resourceNames, resourceNumber, resourceNamesVar, resourceVarsPrefix
		List<RequiredResourcesProperty.Resource> resources = new ArrayList<>();
		resources.add(new RequiredResourcesProperty.Resource("ID1", "r1", "1", "TEST", "TEST", true));
		RequiredResourcesProperty before = new RequiredResourcesProperty(resources);
		project.addProperty(before);
		j.submit(j.createWebClient().getPage(project, "configure").getFormByName("config"));
        // lets see if the config sticks
        RequiredResourcesProperty after = project.getProperty(RequiredResourcesProperty.class);
        assertRequiredResourceList(before.resources, after.resources);

        // build the project and check for results
        LOGGER.info("starting build, may get stuck on resource lock!");
		FreeStyleBuild build = project.scheduleBuild2(0).get();
        LOGGER.info(build.getDisplayName() + " completed");
		String s = FileUtils.readFileToString(build.getLogFile());
        LOGGER.info(s);
        assertTrue("Resource not found environment variables", s.contains("TEST=r1"));
		assertTrue("Resource (r1) not found environment variables", s.contains("TEST1=r1"));
        assertTrue("Resource (r1) desc not found environment variables", s.contains("TEST1_desc=d1"));
        assertTrue("Resource (r1) label not found environment variables", s.contains("TEST1_labels=l1 l2"));
	}

    @Test
    public void testJobConfigWithSingleRequestSingleResourceNestedLabels() throws Exception {
        assertTrue("Not enough resources in the manager", manager.getResources().size() > 0);
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(createEnvDumpStep());

        // setup the list of resources to look for
        // resourceNames, resourceNumber, resourceNamesVar, resourceVarsPrefix
        List<RequiredResourcesProperty.Resource> resources = new ArrayList<>();
        resources.add(new RequiredResourcesProperty.Resource("ID1", "l1 l2", "1", "TEST", "TEST", true));
        RequiredResourcesProperty before = new RequiredResourcesProperty(resources);
        project.addProperty(before);
        j.submit(j.createWebClient().getPage(project, "configure").getFormByName("config"));
        // lets see if the config sticks
        RequiredResourcesProperty after = project.getProperty(RequiredResourcesProperty.class);
        assertRequiredResourceList(before.resources, after.resources);

        // build the project and check for results
        LOGGER.info("starting build, may get stuck on resource lock!");
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        LOGGER.info(build.getDisplayName() + " completed");
        String s = FileUtils.readFileToString(build.getLogFile());
        LOGGER.info(s);
        assertTrue("Resource not found environment variables", s.contains("TEST=r1"));
        assertTrue("Resource (r1) not found environment variables", s.contains("TEST1=r1"));
        assertTrue("Resource (r1) desc not found environment variables", s.contains("TEST1_desc=d1"));
        assertTrue("Resource (r1) label not found environment variables", s.contains("TEST1_labels=l1 l2"));
    }

    @Test
    public void testJobConfigWithSingleRequestMultipleResources() throws Exception {
        addTestResource(new LockableResource(generateUniqueID(), "r2", "", "l1 l2", "", Arrays.asList(new LockableResourceProperty("PROP1", "1"), new LockableResourceProperty("PROP2", "2"))));
        addTestResource(new LockableResource(generateUniqueID(), "r3", null, "l1 l2", "", Arrays.asList(new LockableResourceProperty("PROP1", "3"), new LockableResourceProperty("PROP2", "4"))));
        assertTrue("Not enough resources in the manager", manager.getResources().size() > 2);
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(createEnvDumpStep());

        // setup the list of resources to look for
        // resourceNames, resourceNumber, resourceNamesVar, resourceVarsPrefix
        List<RequiredResourcesProperty.Resource> resources = new ArrayList<>();
        resources.add(new RequiredResourcesProperty.Resource("ID2", "l1", "3", "TEST", "", true));
        RequiredResourcesProperty before = new RequiredResourcesProperty(resources);
        project.addProperty(before);
        j.submit(j.createWebClient().getPage(project, "configure").getFormByName("config"));
        // lets see if the config sticks
        RequiredResourcesProperty after = project.getProperty(RequiredResourcesProperty.class);
        assertRequiredResourceList(before.resources, after.resources);

        // build the project and check for results
        LOGGER.info("starting build, may get stuck on resource lock!");
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        LOGGER.info(build.getDisplayName() + " completed");
        String s = FileUtils.readFileToString(build.getLogFile());
        LOGGER.info(s);
        assertTrue("Resources not found environment variables", s.contains("TEST=r1 r2 r3"));
        assertTrue("Resource (r1) not found environment variables", s.contains("TEST1=r1"));
        assertTrue("Resource (r1) desc not found environment variables", s.contains("TEST1_desc=d1"));
        assertTrue("Resource (r1) label not found environment variables", s.contains("TEST1_labels=l1 l2"));
        assertTrue("Resource (r2) not found environment variables", s.contains("TEST2=r2"));
        assertTrue("Resource (r2) desc not found environment variables", !s.contains("TEST2_desc="));
        assertTrue("Resource (r2) label not found environment variables", s.contains("TEST2_labels=l1 l2"));
        assertTrue("Resource (r3) not found environment variables", s.contains("TEST3=r3"));
        assertTrue("Resource (r3) desc not found environment variables", !s.contains("TEST3_desc="));
        assertTrue("Resource (r3) label not found environment variables", s.contains("TEST3_labels=l1 l2"));
    }

    @Test
    public void testJobConfigWithMultipleRequestMultipleResources() throws Exception {
        addTestResource(new LockableResource(generateUniqueID(), "r2", "d1", "l1 l2", "", Arrays.asList(new LockableResourceProperty("PROP1", "1"), new LockableResourceProperty("PROP2", "2"))));
        addTestResource(new LockableResource(generateUniqueID(), "r3", "d1", "l1 l2", "", Arrays.asList(new LockableResourceProperty("PROP1", "3"), new LockableResourceProperty("PROP2", "4"))));
        assertTrue("Not enough resources in the manager", manager.getResources().size() > 2);
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(createEnvDumpStep());

        // setup the list of resources to look for
        // resourceNames, resourceNumber, resourceNamesVar, resourceVarsPrefix
        List<RequiredResourcesProperty.Resource> resources = new ArrayList<>();
        resources.add(new RequiredResourcesProperty.Resource("ID3", "l1", "1", "TEST1", "", true));
        resources.add(new RequiredResourcesProperty.Resource("ID4", "l1", "1", "TEST2", "TEST2P", true));
        resources.add(new RequiredResourcesProperty.Resource("ID5", "r3", "1", "TEST3", "TEST3", false));
        RequiredResourcesProperty before = new RequiredResourcesProperty(resources);
        project.addProperty(before);
        j.submit(j.createWebClient().getPage(project, "configure").getFormByName("config"));
        // lets see if the config sticks
        RequiredResourcesProperty after = project.getProperty(RequiredResourcesProperty.class);
        assertRequiredResourceList(before.resources, after.resources);

        // build the project and check for results
        LOGGER.info("starting build, may get stuck on resource lock!");
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        LOGGER.info(build.getDisplayName() + " completed");
        String s = FileUtils.readFileToString(build.getLogFile());
        LOGGER.info(s);
        assertTrue("Resource (r1) not found environment variables", (s.contains("TEST1=r1") || s.contains("TEST1=r2")));
        assertTrue("Resource (r2) not found environment variables", (s.contains("TEST2=r1") || s.contains("TEST2=r2")));
        assertTrue("Resource (r3) not found environment variables", s.contains("TEST3=r3"));
    }

    @Test
    public void testJobConfigWithPercentMatchingDefaults() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(createEnvDumpStep());
        // setup the list of resources to look for
        List<RequiredResourcesProperty.Resource> resources = new ArrayList<>();
        resources.add(new RequiredResourcesProperty.Resource("ID3", "l1", "1", "TEST1", "", manager.getUsePercentMatchingDefault()));
        RequiredResourcesProperty before = new RequiredResourcesProperty(resources);
        project.addProperty(before);
        j.submit(j.createWebClient().getPage(project, "configure").getFormByName("config"));
        // lets see if the config sticks
        RequiredResourcesProperty after = project.getProperty(RequiredResourcesProperty.class);
        assertRequiredResourceList(before.resources, after.resources);
    }
}
