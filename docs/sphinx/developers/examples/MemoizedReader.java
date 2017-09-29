/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
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

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import loci.formats.IFormatReader;
import loci.formats.Memoizer;

public class MemoizedReader {

    private static final Logger log =
            LoggerFactory.getLogger(MemoizedReader.class);

    private String input;

    private boolean debug;

    public static void main(String[] args) throws Throwable {
        MemoizedReader main = new MemoizedReader();
        for (int i=0; i<args.length; i++) {
          if (args[i].equals("--input")) {
            main.setInputFile(args[i + 1]);
          }
          else if (args[i].equals("--debug")) {
            main.setDebug(true);
          }
        }
        main.readPlanes();
    }

    public void setInputFile(String input) {
      this.input = input;
    }

    public void setDebug(boolean debug) {
      this.debug = debug;
    }

    private IFormatReader initializeReader(String fileName) throws Exception {
        // by default, Memoizer delegates to an ImageReader for file reading
        // an instance of IFormatReader can be passed to the constructor,
        // in which case that will be used instead of ImageReader
        //
        // when setId is called, a new file named ("." + fileName + ".bfmemo")
        // will be created if the file initialization exceeds a certain number of milliseconds
        // the default timeout is 100ms, but here we override to 0 (i.e. always create the file)
        //
        // .*.bfmemo files are written to the same directory as the initialized file by default,
        // but here we override to use the OS-specific temporary directory
        // whichever directory is supplied needs to be both writeable and readable
        Memoizer reader = new Memoizer(0, new File(System.getProperty("java.io.tmpdir")));
        reader.setId(fileName);
        return reader;
    }

    private void readPlanes() throws Exception {
        // Setup logger
        ch.qos.logback.classic.Logger root =
            (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(
                Logger.ROOT_LOGGER_NAME);
        if (debug) {
            root.setLevel(Level.DEBUG);
        } else {
            root.setLevel(Level.INFO);
        }
        long t0 = System.currentTimeMillis();
        IFormatReader reader = this.initializeReader(this.input);
        long t1 = System.currentTimeMillis();

        log.info("First initialization time: {} ms", t1 - t0);
        reader.close();

        t0 = System.currentTimeMillis();
        reader = this.initializeReader(this.input);
        t1 = System.currentTimeMillis();
        // the logged time here should be noticeably smaller
        log.info("Initialization time with caching: {} ms", t1 - t0);
    }
}
