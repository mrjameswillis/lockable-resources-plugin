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


import hudson.EnvVars;
import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourceTestBase;
import org.jenkins.plugins.lockableresources.queue.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author aki
 */
public class UtilsTest extends LockableResourceTestBase {

    private static final Logger LOGGER = Logger.getLogger(UtilsTest.class.getName());
    public LockableResource instance = new LockableResource("ID1", "r1", "d1", "l1 l2", "", null);

	public UtilsTest() {
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

    @Test
    public void testExpandedVariableInvalid() {
        LOGGER.info("expandVariable");
        EnvVars env = new EnvVars();
        env.put("TEST_VAR", "NA");
        String results = Utils.expandVariable("${INVALID_VAR}", env);
        assertEquals("Results does not equal the unique test.", "${INVALID_VAR}", results);
        String results2 = Utils.expandVariable("%INVALID_VAR%", env);
        assertEquals("Results2 does not equal the unique test.", "${INVALID_VAR}", results2);
    }

    @Test
    public void testExpandedVariable() {
        LOGGER.info("expandVariable");
        EnvVars env = new EnvVars();
        String uniqueID = generateUniqueID();
        env.put("TEST_VAR", uniqueID);
        String results = Utils.expandVariable("${TEST_VAR}", env);
        assertEquals("Results does not equal the unique test.", uniqueID, results);
        String results2 = Utils.expandVariable("%TEST_VAR%", env);
        assertEquals("Results2 does not equal the unique test.", uniqueID, results2);
    }

    @Test
    public void testGetAllExpandedVariablesInvalid() {
        LOGGER.info("getExpandedVariables");
        EnvVars env = new EnvVars();
        env.put("TEST_VAR", "NA");
        String results = Utils.getExpandedVariables("${INVALID_VAR}", env);
        assertEquals("Results does not equal the unique test.", "${INVALID_VAR}", results);
        String results2 = Utils.getExpandedVariables("%INVALID_VAR%", env);
        assertEquals("Results2 does not equal the unique test.", "${INVALID_VAR}", results2);
    }

	@Test
	public void testGetAllExpandedVariablesSingle() {
		LOGGER.info("getExpandedVariables");
		EnvVars env = new EnvVars();
        String uniqueID = generateUniqueID();
		env.put("TEST_VAR", uniqueID);
		String results = Utils.getExpandedVariables("${TEST_VAR}", env);
		assertEquals("Results does not equal the unique test.", uniqueID, results);
        String results2 = Utils.getExpandedVariables("%TEST_VAR%", env);
        assertEquals("Results2 does not equal the unique test.", uniqueID, results2);
	}

    @Test
    public void testGetAllExpandedVariablesMultiple() {
        LOGGER.info("getExpandedVariables");
        EnvVars env = new EnvVars();
        String uniqueID1 = generateUniqueID();
        String uniqueID2 = generateUniqueID();
        env.put("TEST_VAR1", uniqueID1);
        env.put("TEST_VAR2", uniqueID2);
        String results = Utils.getExpandedVariables("${TEST_VAR1} ${TEST_VAR2}", env);
        assertEquals("Results does not equal the unique test or order.", uniqueID1 + " " + uniqueID2, results);
        String results2 = Utils.getExpandedVariables("%TEST_VAR1% %TEST_VAR2%", env);
        assertEquals("Results2 does not equal the unique test or order.", uniqueID1 + " " + uniqueID2, results2);
    }

    @Test
    public void testGetAllExpandedVariablesNested() {
        LOGGER.info("getExpandedVariables Recursion Bug 1");
        EnvVars env = new EnvVars();
        String uniqueID1 = generateUniqueID();
        env.put("TEST_VAR1", uniqueID1);
        env.put("TEST_VAR2", "${TEST_VAR1}");
        env.put("TEST_VAR3", "${TEST_VAR2}");
        env.put("TEST_VAR4", "${TEST_VAR4}");
        String results = Utils.getExpandedVariables("${TEST_VAR1} ${TEST_VAR2} %TEST_VAR3% ${TEST_VAR4}", env);
        assertEquals("Results does not equal the unique test or order.", uniqueID1 + " ${TEST_VAR4}", results);
    }

    @Test
    public void testGetAllExpandedVariablesNestedRecursion() {
        LOGGER.info("getExpandedVariables Recursion Bug 2");
        EnvVars env = new EnvVars();
        env.put("TEST_VAR1", "${TEST_VAR2}");
        env.put("TEST_VAR2", "${TEST_VAR1}");
        String results = Utils.getExpandedVariables("${TEST_VAR1} ${TEST_VAR2} %TEST_VAR3% ${TEST_VAR4}", env);
        assertEquals("Results does not equal the unique test or order.", "${TEST_VAR1} ${TEST_VAR2} ${TEST_VAR3} ${TEST_VAR4}", results);
    }

    @Test
    public void testGetAllExpandedVariablesSetInvalid() {
        LOGGER.info("getExpandedVariablesSet");
        EnvVars env = new EnvVars();
        env.put("TEST_VAR", "NA");
        String results = Utils.getExpandedVariables("${TEST_VAR}", env, 97);
        assertEquals("Results does not equal the unique test.", null, results);
    }

    @Test
    public void testGetAllExpandedVariablesSetSingle() {
        LOGGER.info("getExpandedVariablesSet");
        EnvVars env = new EnvVars();
        String uniqueID = generateUniqueID();
        env.put("TEST_VAR", uniqueID);
        String results = Utils.getExpandedVariables("${TEST_VAR}", env, 1);
        assertEquals("Results does not equal the unique test.", uniqueID, results);
        String results2 = Utils.getExpandedVariables("%TEST_VAR%", env, 1);
        assertEquals("Results2 does not equal the unique test.", uniqueID, results2);
    }

    @Test
    public void testGetAllExpandedVariablesSetMultiple() {
        LOGGER.info("getExpandedVariablesSet");
        EnvVars env = new EnvVars();
        String uniqueID1 = generateUniqueID();
        String uniqueID2 = generateUniqueID();
        env.put("TEST_VAR1", uniqueID1);
        env.put("TEST_VAR2", uniqueID2);
        String results = Utils.getExpandedVariables("${TEST_VAR1} ${TEST_VAR2}", env, 2);
        assertEquals("Results does not equal the unique test or order.", uniqueID1 + " " + uniqueID2, results);
        String results2 = Utils.getExpandedVariables("%TEST_VAR1% %TEST_VAR2%", env, 2);
        assertEquals("Results2 does not equal the unique test or order.", uniqueID1 + " " + uniqueID2, results2);
    }

    @Test
    public void testGetAllExpandedVariablesSetNested() {
        LOGGER.info("getExpandedVariablesSet");
        EnvVars env = new EnvVars();
        String uniqueID1 = generateUniqueID();
        env.put("TEST_VAR1", uniqueID1);
        env.put("TEST_VAR2", "${TEST_VAR1}");
        env.put("TEST_VAR3", "${TEST_VAR2}");
        env.put("TEST_VAR4", "${TEST_VAR4}");
        String results = Utils.getExpandedVariables("${TEST_VAR1} ${TEST_VAR2} %TEST_VAR3% ${TEST_VAR4}", env, 2);
        assertEquals("Results does not equal the unique test or order.", uniqueID1 + " ${TEST_VAR4}", results);
    }

    @Test
    public void testGetExpandedListOfVariablesInvalid() {
        LOGGER.info("getExpandedListOfVariables");
        EnvVars env = new EnvVars();
        env.put("TEST_VAR", "NA");
        Set<String> invalid = new LinkedHashSet<>(Arrays.asList("${INVALID_VAR}".split("\\s+")));
        Set<String> results = Utils.getExpandedListOfVariables(invalid, env);
        assertEquals("Results does not equal the unique test.", invalid, results);
    }

    @Test
    public void testGetExpandedListOfVariablesSingle() {
        LOGGER.info("getExpandedListOfVariables");
        EnvVars env = new EnvVars();
        String uniqueID = generateUniqueID();
        env.put("TEST_VAR", uniqueID);
        Set<String> valid = new LinkedHashSet<>(Arrays.asList(uniqueID.split("\\s+")));

        Set<String> results = Utils.getExpandedListOfVariables(new LinkedHashSet<>(Arrays.asList("${TEST_VAR}".split("\\s+"))), env);
        assertEquals("Results does not equal the unique test.", valid, results);
        Set<String> results2 = Utils.getExpandedListOfVariables(new LinkedHashSet<>(Arrays.asList("%TEST_VAR%".split("\\s+"))), env);
        assertEquals("Results2 does not equal the unique test.", valid, results2);
    }

    @Test
    public void testGetExpandedListOfVariablesMultiple() {
        LOGGER.info("getExpandedListOfVariables");
        EnvVars env = new EnvVars();
        String uniqueID1 = generateUniqueID();
        String uniqueID2 = generateUniqueID();
        env.put("TEST_VAR1", uniqueID1);
        env.put("TEST_VAR2", uniqueID2);
        Set<String> valid = new LinkedHashSet<>(Arrays.asList((uniqueID1 + " " + uniqueID2).split("\\s+")));

        Set<String> results = Utils.getExpandedListOfVariables(new LinkedHashSet<>(Arrays.asList("${TEST_VAR1} ${TEST_VAR2}".split("\\s+"))), env);
        assertEquals("Results does not equal the unique test or order.", valid, results);
        Set<String> results2 = Utils.getExpandedListOfVariables(new LinkedHashSet<>(Arrays.asList("%TEST_VAR1% %TEST_VAR2%".split("\\s+"))), env);
        assertEquals("Results2 does not equal the unique test or order.", valid, results2);
    }

    @Test
    public void testGetExpandedListOfVariablesNested() {
        LOGGER.info("getExpandedListOfVariables");
        EnvVars env = new EnvVars();
        String uniqueID1 = generateUniqueID();
        env.put("TEST_VAR1", uniqueID1);
        env.put("TEST_VAR2", "${TEST_VAR1}");
        env.put("TEST_VAR3", "${TEST_VAR2}");
        env.put("TEST_VAR4", "${TEST_VAR4}");
        Set<String> valid = new LinkedHashSet<>(Arrays.asList((uniqueID1 + " ${TEST_VAR4}").split("\\s+")));

        Set<String> results = Utils.getExpandedListOfVariables(new LinkedHashSet<>(Arrays.asList("${TEST_VAR1} ${TEST_VAR2} %TEST_VAR3% ${TEST_VAR4}".split("\\s+"))), env);
        assertEquals("Results does not equal the unique test or order.", valid, results);
    }
}
