package com.tme_inc.pwv

/**
 * Created by dennis on 1/29/15.
 */

// message used by PW Stream

internal const val MSG_PW_QUIT = 101
internal const val MSG_PW_CONNECT = 102
internal const val MSG_PW_GETFRAME = 103
internal const val MSG_PW_SEEK = 104      //  arg1=date (yyyymmdd), arg2=time of the day (hhMMss)
internal const val MSG_PW_GETCLIPLIST = 105      //  arg1=date (yyyymmdd)

// live view screen to turn off LP button selection
internal const val MSG_PW_LPOFF = 106

// message used by TimeBar

internal const val MSG_TB_SCROLL = 201
internal const val MSG_TB_GETCLIPLIST = 202

// message from VRI list selection
internal const val MSG_VRI_SELECTED = 301

// message from Select Date dialog
internal const val MSG_DATE_SELECTED = 302       // arg1 = date (bcd: yyyymmdd)

// message from Select DVR dialog
internal const val MSG_DVR_SELECTED = 303       // arg1 = date (bcd: yyyymmdd)

