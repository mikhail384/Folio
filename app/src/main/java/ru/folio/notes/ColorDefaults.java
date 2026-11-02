package ru.folio.notes;

/** RGB palette facts from the supplied Mi Canvas reference; UI and rendering are Folio code. */
final class ColorDefaults {
    static final int COLUMNS=13,ROWS=9;
    static final int[] GRID={
        0xfffecdcb, 0xffffdac0, 0xffffe7c0, 0xffffeec3, 0xfffff5bf, 0xffe4f8c0, 0xffc6f3ce, 0xffc3f2f2, 0xffcce0ff, 0xffddd9fd, 0xffedd2fd, 0xfff9cfe5, 0xffffffff,
        0xfffdb9b6, 0xffffcba7, 0xffffdda7, 0xffffe7ab, 0xfffff2a6, 0xffdaf5a7, 0xffb0edba, 0xffabecec, 0xffb8d3ff, 0xffcfcafd, 0xffe6c0fc, 0xfff7bcda, 0xffe5e5e5,
        0xfffc928c, 0xffffad76, 0xffffca76, 0xffffda7b, 0xffffea73, 0xffc4ef74, 0xff83e393, 0xff7be2e2, 0xff8fbaff, 0xffb4abfc, 0xffd79bfb, 0xfff296c5, 0xffcccccc,
        0xfffb6058, 0xffff8837, 0xffffb237, 0xffffc93f, 0xffffe133, 0xffa9e835, 0xff4ad761, 0xff3fd5d5, 0xff5d9bff, 0xff9285fa, 0xffc56ef9, 0xffec66aa, 0xffa6a6a6,
        0xfffa382e, 0xffff6a05, 0xffff9f05, 0xffffbb0f, 0xffffd900, 0xff94e203, 0xff1dcd3a, 0xff0fcaca, 0xff3482ff, 0xff7767f9, 0xffb74af7, 0xffe74095, 0xff808080,
        0xffd53027, 0xffd95a04, 0xffd98704, 0xffd99f0d, 0xffd9b800, 0xff7ec003, 0xff19ae31, 0xff0dacac, 0xff2c6fd9, 0xff6558d4, 0xff9c3fd2, 0xffc4367f, 0xff606060,
        0xffaa261f, 0xffad4803, 0xffad6c03, 0xffad7f0a, 0xffad9400, 0xff659a02, 0xff148b27, 0xff0a8989, 0xff2358ad, 0xff5146a9, 0xff7c32a8, 0xff9d2c65, 0xff464646,
        0xff7d1c17, 0xff803503, 0xff805003, 0xff805e08, 0xff806d00, 0xff4a7102, 0xff0f671d, 0xff086565, 0xff1a4180, 0xff3c347d, 0xff5c257c, 0xff74204b, 0xff2d2d2d,
        0xff641612, 0xff662a02, 0xff664002, 0xff664b06, 0xff665700, 0xff3b5a01, 0xff0c5217, 0xff054747, 0xff122e59, 0xff2a2457, 0xff401a56, 0xff511634, 0xff000000
    };
    private static final int[] BALL={0xff1f1f1f,0xff1d39c4,0xffff4d4f};
    private static final int[] FOUNTAIN={0xff40a9ff,0xffffc53d,0xffff7a45};
    private static final int[] MARKER={0xffbae7ff,0xfffffb8f,0xffffd8bf};
    private static final int[] PENCIL={0xff096dd9,0xff000000,0xffcf1322};
    private static final int[] BRUSH={0xff87e8de,0xffffa39e,0xffffadd2};
    static int[] defaults(String tool){switch(tool){case "fountain":return FOUNTAIN;case "marker":return MARKER;case "pencil":return PENCIL;case "brush":return BRUSH;default:return BALL;}}
    static int first(String tool){return defaults(tool)[0];}
    static int[] forTool(String tool){return defaults(tool).clone();}
    static int hit(float x,float y,float width,float height){if(!Float.isFinite(x)||!Float.isFinite(y)||width<=0||height<=0||x<0||y<0||x>=width||y>=height)return -1;return Math.min(ROWS-1,(int)(y/height*ROWS))*COLUMNS+Math.min(COLUMNS-1,(int)(x/width*COLUMNS));}
}
