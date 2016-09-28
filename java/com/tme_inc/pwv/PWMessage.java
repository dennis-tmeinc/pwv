package com.tme_inc.pwv;

/**
 * Created by dennis on 1/29/15.
 */
public class PWMessage {

    // used by main activity
    public static final int MSG_UI_HIDE         =  1 ;

    // message used by PW Stream
    public static final int MSG_PW_QUIT         =  101 ;
    public static final int MSG_PW_CONNECT      =  102 ;
    public static final int MSG_PW_GETFRAME     =  103 ;
    public static final int MSG_PW_SEEK         =  104 ;      //  arg1=date (yyyymmdd), arg2=time of the day (hhMMss)
    public static final int MSG_PW_GETCLIPLIST  =  105 ;      //  arg1=date (yyyymmdd)

    // live view screen to turn off LP button selection
    public static final int MSG_PW_LPOFF        =  106 ;

    // message used by TimeBar
    public static final int MSG_TB_SCROLL       =  201 ;
    public static final int MSG_TB_GETCLIPLIST  =  202 ;

    // message from VRI list selection
    public static final int MSG_VRI_SELECTED    =  301 ;

    // message from Select Date dialog
    public static final int MSG_DATE_SELECTED   =  302 ;       // arg1 = date (bcd: yyyymmdd)

    // message from Select DVR dialog
    public static final int MSG_DVR_SELECTED    =  303 ;       // arg1 = date (bcd: yyyymmdd)


}
