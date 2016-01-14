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

package org.jenkins.plugins.lockableresources;


import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test Base class for all tests
 * @author jwillis
 */
public class LockableResourceTestBase {

	private static final Logger LOGGER = Logger.getLogger(LockableResourceTestBase.class.getName());
	public Map<String, LockableResource> lockableResources = new HashMap<>();
	public LockableResourcesManager manager;

	@Rule
	public JenkinsRule j = null;

	public LockableResourceTestBase() {
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		if (j != null)
			manager = j.jenkins.getPlugin(LockableResourcesManager.class);
	}

	@After
	public void tearDown() throws Exception {
	}

	public static String generateUniqueID() {
		return UUID.randomUUID().toString();
	}

	public void addTestResource(LockableResource lockableResource) throws IOException {
		manager.load(); // make sure we load any data
		manager.getResources().add(lockableResource);
		this.lockableResources.put(lockableResource.getName(), lockableResource);
		manager.save(); // save it for use
	}

	public void addTestResources(LockableResource ... lockableResources) throws IOException {
		manager.load(); // make sure we load any data
		for (LockableResource lockableResource : lockableResources) {
			manager.getResources().add(lockableResource);
			this.lockableResources.put(lockableResource.getName(), lockableResource);
		}
		manager.save(); // save it for use
	}

	public void removeTestResource(LockableResource lockableResource) throws IOException {
		manager.load(); // make sure we load any data
		manager.getResources().remove(lockableResource);
		this.lockableResources.remove(lockableResource.getName());
		manager.save(); // save it for use
	}

	public void reserveTestResource(LockableResource lockableResource) throws IOException {
		manager.load(); // make sure we load any data
		manager.reserve(Collections.singletonList(lockableResource), "Mr. Robot");
		manager.save(); // save it for use
	}

	public void unreserveTestResource(LockableResource lockableResource) throws IOException {
		manager.load(); // make sure we load any data
		manager.unreserve(Collections.singletonList(lockableResource));
		manager.save(); // save it for use
	}

	public void clearTestResources() throws IOException {
		manager.load(); // make sure we load any data
		manager.getResources().clear();
		this.lockableResources.clear();
		manager.save(); // save it for use
	}

	public Builder createEchoStep(String text) {
		// if unix new Shell("echo hello")
		return new BatchFile("echo " + text);
	}

	public Builder createEnvDumpStep() {
		// if unix new Shell("env")
		return new BatchFile("SET");
	}

	public LockableResource getLockableResourceFromDump(String key, String dump) throws IOException {
		manager.load(); // make sure we load any data
		String tmp = dump.substring(dump.indexOf(key + "="));
		tmp = tmp.substring(key.length() + 1, tmp.indexOf("\n") - 1);
		LockableResource mResource = manager.fromName(tmp);
		LockableResource lResource = this.lockableResources.get(tmp);
		LOGGER.info("Found resource from name " + tmp + " : " + lResource);
		assertEquals("Local and manager resource are not the same!", lResource, mResource);
		return lResource;
	}

	public void assertLockableResourcesInManager() throws IOException {
		manager.load(); // make sure we load any data
		assertEquals("Local and manager size are out of sync!", lockableResources.size(), manager.getResources().size());
		for (LockableResource r : manager.getResources()) {
			assertTrue("Local copy doesn't contain key resource " + r.getName(), lockableResources.containsKey(r.getName()));
			assertTrue("Local copy doesn't contain value resource " + r.getName(), lockableResources.containsValue(r));
		}
	}

	public void assertRequiredResourceList(List<RequiredResourcesProperty.Resource> before,
										   List<RequiredResourcesProperty.Resource> after) {
		assertEquals("The before resource size (" + before.size() + ") is not the same as after (" + after.size() + ")",
				before.size(), after.size());
		for (RequiredResourcesProperty.Resource b : before) {
			boolean foundResource = false;
			for (RequiredResourcesProperty.Resource a : after) {
				LOGGER.info("Comparing " + b.getUniqueID() + " to " + a.getUniqueID());
				if (b.getUniqueID().equals(a.getUniqueID())) {
					foundResource = true;
					break;
				}
			}
			assertTrue("Resource [" + b.getUniqueID() + "] is not in after list!", foundResource);
		}
	}
}
