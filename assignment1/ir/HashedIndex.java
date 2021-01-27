/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  


package ir;

import java.util.HashMap;
import java.util.Iterator;


/**
 *   Implements an inverted index as a Hashtable from words to PostingsLists.
 */
public class HashedIndex implements Index {


    /** The index as a hashtable. */
    private HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();


    /**
     *  Inserts this token in the hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        PostingsList post_list = getPostings(token);
        PostingsEntry post_entry = new PostingsEntry();
        post_entry.docID = docID;
        if (post_list == null){
            PostingsList p_list = new PostingsList();
            p_list.set(post_entry);
            index.put(token,p_list);
        }else{
           post_list.set(post_entry);
        }
    }


    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
        PostingsList post_list;
        post_list = index.get(token);
        return post_list;
    }


    /**
     *  No need for cleanup in a HashedIndex.
     */
    public void cleanup() {
    }
}
