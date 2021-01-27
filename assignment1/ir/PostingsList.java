/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.Iterator;

public class PostingsList {
    
    /** The postings list */
    private ArrayList<PostingsEntry> list = new ArrayList<PostingsEntry>();


    /** Number of postings in this list. */
    public int size() {
    return list.size();
    }

    /** Returns the ith posting. */
    public PostingsEntry get( int i ) {
    return list.get( i );
    }

    /** Puts a new Entry)*/
    public void set(PostingsEntry postentry) {
        if (list.size()==0) {
            list.add(postentry);
        }
        else if (postentry.docID != list.get(list.size() - 1).docID){
            list.add(postentry);
        }
        else if (postentry.docID == list.get(list.size() - 1).docID){
            int new_offset = postentry.offsetList.get(0);
            list.get(list.size() - 1).offsetList.add(new_offset);
        }
    }
    // 
    //  YOUR CODE HERE
    //
}

