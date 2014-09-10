/*
 * #%L
 * BSD implementations of Bio-Formats readers and writers
 * %%
 * Copyright (C) 2005 - 2014 Open Microscopy Environment:
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
 * #L%
 */

package loci.formats;

import java.util.EnumSet;

/**
 * Enumeration of possible domains to which a format could belong.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/FormatDomain.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/FormatDomain.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public enum FormatDomain {

  HCS("High-Content Screening (HCS)", true),
  LM("Light Microscopy", false),
  EM("Electron Microscopy (EM)", false),
  SPM("Scanning Probe Microscopy (SPM)", false),
  SEM("Scanning Electron Microscopy (SEM)", false),
  FLIM("Fluorescence-Lifetime Imaging", false),
  MEDICAL("Medical Imaging", false),
  HISTOLOGY("Histology", false),
  GEL("Gel/Blot Imaging", false),
  ASTRONOMY("Astronomy", false),
  GRAPHICS("Graphics", true),
  UNKNOWN("Unknown", false);

  private final String name;
  private final boolean special;

  private FormatDomain(String name, boolean requiresSpecialHandling) {
    this.name = name;
    this.special = requiresSpecialHandling;
  }

  /** Return the name of the domain. */
  public String getName() {
    return name;
  }

  /**
   * Return whether or not formats in this domain are likely to require special handling.
   */
  public boolean isSpecial() {
    return special;
  }

  /**
   * Return all known domains as an array.
   */
  public static FormatDomain[] getAllDomains() {
    EnumSet set = EnumSet.allOf(FormatDomain.class);
    return (FormatDomain[]) set.toArray(new FormatDomain[set.size()]);
  }

  /**
   * Return all known domain names as an array.
   */
  public static String[] getAllDomainNames() {
    FormatDomain[] domains = getAllDomains();
    String[] names = new String[domains.length];
    for (int i=0; i<names.length; i++) {
      names[i] = domains[i].getName();
    }
    return names;
  }

}
