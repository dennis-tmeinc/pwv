package com.tme_inc.pwv;

/**
 * Created by dennis on 24/11/15.
 *    These codes are broken RC4, but has been used all over TME dvr codes
 */
public class PWRc4 {

    private byte s[] = new byte[256] ;
    private int  si, sj ;

    // RC4 key-scheduling algorithm
    //     k: key array, 256 bytes
    // initialize RC4 seed with key block
    public void RC4_KSA( byte [] k )
    {
        for( si=0; si<256; si++ )
            s[si]=(byte)si;
        si=sj=0;
        for( int n=0; n<k.length; n++ ) {
            si = n & 0xff ;
            sj = (sj+s[si]+k[n]) & 0xff ;

            // swap(s[i], s[j])
            byte swap=s[si];
            s[si]=s[sj];
            s[sj]=swap;
        }
        si=sj=0;
    }

    // *** RC4 block cryption.
    //   Since RC4 is a stream cryption, not a block cryption.
    // So we use RC4 PRGA to generate a block of pesudo random data, encrypt/decrypt
    // by xor original message with this data.

    // RC4 PRGA
    //   The pseudo-random generation algorithm
    public byte RC4_PRGA()
    {
        si = (si+1) & 0xff ;
        sj = (sj+s[si]) & 0xff ;

        // swap( s[i], s[j])
        byte swap=s[si] ;
        s[si]=s[sj] ;
        s[sj]=swap ;

        // The correct RC4 should be:  s[ (s[si] + s[sj]) & 0xff ] ;
        return (byte)(swap + s[si]) ;
    }

    // RC4 stream data cryption.
    //     text: data to be encrypt/decrypt
    //     offset, len: data size
    // to decrypt/encrypt text, both seed and text will be changed.
    public void RC4_crypt( byte [] text, int offset, int len ){
        // PRGA
        for( int n=0; n<len; n++) {
            text[offset+n]^=RC4_PRGA() ;
        }
    }

    // Generate RC4 cryption table
    //      crypt_table: cryption table for block encryption
    //      k: initial key
    public static void RC4_crypt_table( byte [] crypt_table, byte []  k)
    {
        PWRc4 rc4=new PWRc4() ;
        rc4.RC4_KSA(k);		    // generate seed ;
        for(int i=0; i<crypt_table.length; i++) {
            crypt_table[i]=rc4.RC4_PRGA();
        }
    }


    // RC4 block cryption
    //    text: data to be encrypt/decrypt
    //    textsize: size of data
    //    textoffset: offset of data from start of file (0 for start of file or independent data)
    //    crypt_table: cryption table
    //    table_size: cryption table size
    public static void RC4_block_crypt( byte[] text, int offset, int textsize, int textoffset, byte [] crypt_table)
    {
        for( int i=0; i<textsize; i++) {
            text[offset+i]^=crypt_table[(i+textoffset)%crypt_table.length] ;
        }
    }


}
