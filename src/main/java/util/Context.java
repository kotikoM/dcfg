package util;

public class Context {
    public static int BPT = 28;
    public static int SPT = 29;
    public static int HPT = 30;
    public static int SBASE = 30;
    public static int HBASE = 31;
    public static int HMAX = 31;
    public static int SMAX = 32;
    public static int RA = 31;
    public static boolean DEBUG = true;
    // only for abstract kernel
    public static int programInit = 14; // number of words preceding program;
    public static int gammaAddress = 1564; // address of gamma - save user begins in abstract kernel.  
    public static int bootLoaderInit = 4;
    public static int removedStatementsForBootLoader = 3;
}
