/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013-2015, 6WIND S.A.                                 *
 *                          SAP SE                                     *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources;

/**
 * Defines certain plugin-wide constants.
 */
public class Constants {
	/**
	 * Regex for splitting strings which list multiple resources.
	 */
	public static final String RESOURCES_SPLIT_REGEX = "\\s+";
	
	/**
	 * Plugin icon file (24x24).
	 */
	public static final String ICON_SMALL = "/plugin/lockable-resources/img/device-24x24.png";

	/**
	 * Plugin icon file (48x48).
	 */
	public static final String ICON_LARGE = "/plugin/lockable-resources/img/device-48x48.png";

	/**
	 * Prefix for groovy expression to evaluate potential resources.
	 */
	public static final String GROOVY_LABEL_MARKER = "groovy:";

    /**
     * Prefix for label expression to evaluate potential resources.
     */
    public static final String EXACT_LABEL_MARKER = "label:";

	/**
	 * The default use percent matching setting (in global).
	 */
	public static final boolean DEFAULT_USE_PERCENT_MATCHING = false;
}
