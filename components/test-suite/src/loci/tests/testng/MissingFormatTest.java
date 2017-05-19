/*
 * #%L
 * OME Bio-Formats manual and automated test suite.
 * %%
 * Copyright (C) 2017 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package loci.tests.testng;

import java.io.File;

import loci.common.DataTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

/**
 * Checks the performance test configuration file for missing formats.
 *
 */
public class MissingFormatTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MissingFormatTest.class);

  private String[] configuredReaders;
  private String[] supportedReaders;

  @BeforeClass
  public void init() throws Exception {
    String id = TestTools.getProperty("testng.filename");
    String[] files = DataTools.readFile(id).split("\n");
    configuredReaders = new String[files.length];

    ImageReader reader = new ImageReader();
    IFormatReader[] readers = reader.getReaders();
    supportedReaders = new String[readers.length];
    for (int i=0; i<supportedReaders.length; i++) {
      supportedReaders[i] = readers[i].getClass().getName();
    }

    for (int i=0; i<configuredReaders.length; i++) {
      try {
        reader.setId(files[i]);
        configuredReaders[i] = reader.getReader().getClass().getName();
      }
      catch (Exception e) {
        LOGGER.debug("Failed to initialize {}", files[i]);
      }
      finally {
        reader.close();
      }
    }
  }

  @Test
  public void testMissing() throws Exception {
    boolean fail = false;
    for (int i=0; i<supportedReaders.length; i++) {
      boolean configured = DataTools.indexOf(configuredReaders, supportedReaders[i]) >= 0;
      if (!configured) {
        LOGGER.warn("Missing configuration for reader {}", supportedReaders[i]);
        fail = true;
      }
    }
    assert !fail;
  }

}
