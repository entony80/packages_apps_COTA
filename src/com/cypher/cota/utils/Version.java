package com.cypher.cota.utils;

import java.io.Serializable;

/**
 * Class to manage different versions in the zip name.
 * <p/>
 * Format<br>
 * cypher_A_B-C.D.E-F-H.zip<br>
 * where<br>
 * A = device name, required<br>
 * B = android version<br>
 * C = major, integer from 0 to n, required<br>
 * D = minor, integer from 0 to 9, required<br>
 * E = maintenance, integer from 0 to n, not required<br>
 * F = build type (e.g. NIGHTLY, WEEKLY)<br>
 * G = date, YYYYMMDD, not required, the format can be YYYYMMDDx where x is a
 * letter (for gapps)
 * <p/>
 * All the default values not specified above are 0
 * <p/>
 * Examples<br>
 * cypher_h811_mm-2.3.1-NIGHTLY-20160717.zip<br>
 */
public class Version implements Serializable {

    private static final String SEPARATOR = "-";

    private static final int UNOFFICIAL = 0;
    private static final int EXPERIMENTAL = 1;
    private static final int NIGHTLY = 2;
    private static final int WEEKLY = 3;
    private static final int MONTHLY = 4;
    private static final int RELEASE = 5;

    private final String[] TYPES = {
      "UNOFFICIAL", "EXPERIMENTAL", "NIGHTLY", "WEEKLY", "MONTHLY", "RELEASE"
    };

    private String mDevice;

    private int mMajor = 0;
    private int mMinor = 0;
    private int mMaintenance = 0;

    private int mType = RELEASE;

    private String mDate = "0";

    public Version() {
    }

    public Version(String fileName) {
        String[] STATIC_REMOVE = {
            ".zip", "cypher_", "_mm", "_n"
        };
        for (String remove : STATIC_REMOVE) {
            fileName = fileName.replace(remove, "");
        }

        String[] split = fileName.split(SEPARATOR);

        mDevice = split[0];

        // remove gapps extra names (modular, full, mini, etc)
        while (split[1].matches("\\w+\\.?")) {
            String[] newSplit = new String[split.length - 1];
            newSplit[0] = split[0];
            System.arraycopy(split, 2, newSplit, 1, split.length - 2);
            split = newSplit;
            if (split.length <= 1) {
                break;
            }
        }

        if (split.length <= 1) {
            // malformed version
            return;
        }

        try {
            String version = split[1];
            int index = -1;
            if ((index = version.indexOf(".")) > 0) {
                mMajor = Integer.parseInt(version.substring(0, index));
                version = version.substring(index + 1);
                if (version.length() > 0) {
                    mMinor = Integer.parseInt(version.substring(0, 1));
                }
                if (version.length() > 1) {
                    String maintenance = version.substring(1);
                    if (maintenance.startsWith(".")) {
                        maintenance = maintenance.substring(1);
                    }
                    mMaintenance = Integer.parseInt(maintenance);
                }
            }
            else {
                mMajor = Integer.parseInt(version);
            }

            version = split[2];

            // "UNOFFICIAL", "EXPERIMENTAL", "NIGHTLY", "WEEKLY", "MONTHLY", "RELEASE"
            if (version.startsWith("UNOFFICIAL")) {
               mType = UNOFFICIAL;
            }
            else if (version.startsWith("EXPERIMENTAL")) {
               mType = EXPERIMENTAL;
            }
            else if (version.startsWith("NIGHTLY")) {
               mType = NIGHTLY;
            }
            else if (version.startsWith("WEEKLY")) {
               mType = WEEKLY;
            }
            else if (version.startsWith("MONTHLY")) {
               mType = MONTHLY;
            }
            else {
               mType = RELEASE;
            }

            mDate = split[3];
        } catch (NumberFormatException ex) {
            // malformed version, write the log and continue
            // C derped something for sure
            ex.printStackTrace();
        }
    }

    public static Version fromGapps(String platform, String version) {
        return new Version("gapps-" + platform.substring(0, 1) + "."
                + (platform.length() > 1 ? platform.substring(1) : "") + "-" + version);
    }

    public static int compare(Version v1, Version v2) {
        if (v1.getMajor() != v2.getMajor()) {
            return v1.getMajor() < v2.getMajor() ? -1 : 1;
        }
        if (v1.getMinor() != v2.getMinor()) {
            return v1.getMinor() < v2.getMinor() ? -1 : 1;
        }
        if (v1.getMaintenance() != v2.getMaintenance()) {
            return v1.getMaintenance() < v2.getMaintenance() ? -1 : 1;
        }
        if (v1.getType() != v2.getType()) {
            return v1.getType() < v2.getType() ? -1 : 1;
        }
        if (!v1.getDate().equals(v2.getDate())) {
            return v1.getDate().compareTo(v2.getDate());
        }
        return 0;
    }

    public String getDevice() {
        return mDevice;
    }

    public int getMajor() {
        return mMajor;
    }

    public int getMinor() {
        return mMinor;
    }

    public int getMaintenance() {
        return mMaintenance;
    }

    public int getType() {
        return mType;
    }

    public String getTypeName() {
        return TYPES[mType];
    }

    public String getDate() {
        return mDate;
    }

    public boolean isEmpty() {
        return mMajor == 0;
    }

    public String toString() {
        return toString(true);
    }

    public String toString(boolean showDevice) {
        return (showDevice ? mDevice + " " : "")
                + mMajor
                + "."
                + mMinor
                + (mMaintenance > 0 ? "."
                + mMaintenance : "")
                + (mType != RELEASE ? " " + getTypeName() : "")
                + " (" + mDate + ")";
    }
}
