/*
 * #%L
 * OME SCIFIO package for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2005 - 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package loci.formats.tools;

import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.UpgradeChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class containing methods useful for many command line tools.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/tools/CommandLineTool.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/tools/CommandLineTool.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class CommandLineTool {

  private static final Logger LOGGER =
    LoggerFactory.getLogger(CommandLineTool.class);

  public static final String NO_UPGRADE_CHECK = "-no-upgrade";

  public static void logUpgradeCheck() {
    UpgradeChecker checker = new UpgradeChecker();
    boolean canUpgrade =
      checker.newVersionAvailable(UpgradeChecker.DEFAULT_CALLER);
    if (canUpgrade) {
      LOGGER.info("*** A new stable version is available. ***");
      LOGGER.info("*** Install the new version using:     ***");
      LOGGER.info("***   'upgradechecker -install'        ***");
    }
  }

  public static void logVersion() {
    LOGGER.info("Version: {}", FormatTools.VERSION);
    LOGGER.info("VCS revision: {}", FormatTools.VCS_REVISION);
    LOGGER.info("Build date: {}", FormatTools.DATE);
  }

  public static void logUsage(String description, String command,
    HashMap<String, String> flagArguments, HashMap<String> flagDescriptions)
  {
    LOGGER.info(description);

    for (String flag : flagDescriptions.keySet()) {
      LOGGER.info("{}: {}", flag, flagDescriptions.get(flag));
    }
  }

}
