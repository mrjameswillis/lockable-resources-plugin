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

package org.jenkins.plugins.lockableresources.api;


import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourceTestBase;
import org.junit.*;

import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 *
 * @author aki
 */
public class LockableResourceTest extends LockableResourceTestBase {

    private static final Logger LOGGER = Logger.getLogger(LockableResourceTest.class.getName());
    public LockableResource instance = new LockableResource("ID1", "r1", "d1", "l1 l2", "", null);

	public LockableResourceTest() {
        super();
	}

	@Before
	public void setUp() throws Exception {
        super.setUp();
	}

	@After
	public void tearDown() throws Exception {
        super.tearDown();
	}

	/**
	 * Test of getName method, of class LockableResource.
	 */
	@Test
	public void testGetName() {
		LOGGER.info("getName");
		assertEquals("r1", instance.getName());
	}

	/**
	 * Test of getDescription method, of class LockableResource.
	 */
	@Test
	public void testGetDescription() {
		LOGGER.info("getDescription");
		assertEquals("d1", instance.getDescription());
	}

	/**
	 * Test of getLabels method, of class LockableResource.
	 */
	@Test
	public void testGetLabels() {
		LOGGER.info("getLabels");
		assertEquals("l1 l2", instance.getLabels());
	}

	/**
	 * Test of getReservedBy method, of class LockableResource.
	 */
	@Test
	public void testGetReservedBy() {
		LOGGER.info("getReservedBy");
		assertEquals(null, instance.getReservedBy());
	}

	/**
	 * Test of isReserved method, of class LockableResource.
	 */
	@Test
	public void testIsReserved() {
		LOGGER.info("isReserved");
		assertEquals(false, instance.isReserved());
	}

	/**
	 * Test of isQueued method, of class LockableResource.
	 */
	@Test
	public void testIsQueued_0args() {
		LOGGER.info("isQueued");
		assertEquals(false, instance.isQueued());
	}

	/**
	 * Test of isQueued method, of class LockableResource.
	 */
	@Test
	public void testIsQueued_int() {
		LOGGER.info("isQueued");
		assertEquals(false, instance.isQueued(0));
	}

	/**
	 * Test of isQueuedByTask method, of class LockableResource.
	 */
	@Test
	public void testIsQueuedByTask() {
		LOGGER.info("isQueuedByTask");
		assertEquals(false, instance.isQueuedByTask(1));
	}

	/**
	 * Test of unqueue method, of class LockableResource.
	 */
	@Test
	public void testUnqueue() {
		LOGGER.info("unqueue");
		instance.unqueue();
	}

	/**
	 * Test of isLocked method, of class LockableResource.
	 */
	@Test
	public void testIsLocked() {
		LOGGER.info("isLocked");
		assertEquals(false, instance.isLocked());
	}

	/**
	 * Test of getBuild method, of class LockableResource.
	 */
	@Test
	public void testGetBuild() {
		LOGGER.info("getBuild");
		assertEquals(null, instance.getBuild());
	}

	/**
	 * Test of setBuild method, of class LockableResource.
	 */
	@Test
	public void testSetBuild() {
		LOGGER.info("setBuild");
		instance.setBuild(null)	;
	}

	/**
	 * Test of getQueueItemId method, of class LockableResource.
	 */
	@Test
	public void testGetQueueItemId() {
		LOGGER.info("getQueueItemId");
		assertEquals(0, instance.getQueueItemId());
	}

	/**
	 * Test of getQueueItemProject method, of class LockableResource.
	 */
	@Test
	public void testGetQueueItemProject() {
		LOGGER.info("getQueueItemProject");
		assertEquals(null, instance.getQueueItemProject());
	}

	/**
	 * Test of setReservedBy method, of class LockableResource.
	 */
	@Test
	public void testSetReservedBy() {
		LOGGER.info("setReservedBy");
		instance.setReservedBy("");
	}

	/**
	 * Test of unReserve method, of class LockableResource.
	 */
	@Test
	public void testUnReserve() {
		LOGGER.info("unReserve");
		instance.unReserve();
	}

	/**
	 * Test of reset method, of class LockableResource.
	 */
	@Test
	public void testReset() {
		LOGGER.info("reset");
		instance.reset();
	}

	/**
	 * Test of toString method, of class LockableResource.
	 */
	@Test
	public void testToString() {
		LOGGER.info("toString");
		assertEquals("r1", instance.toString());
	}


	/**
	 * Test of equals method, of class LockableResource.
	 */
	@Test
	public void testEquals() {
		LOGGER.info("equals");
		assertEquals(false, instance.equals(null));
	}

	/**
	 * Test of hashCode method, of class LockableResource.
	 */
	@Test
	public void testHashCode() {
		LOGGER.info("hashCode");
		int result = instance.hashCode();
        LOGGER.info("HASH: " + result);
        assertEquals(111073, result);
	}
}
