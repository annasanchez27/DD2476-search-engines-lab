/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.io.Serializable;

public class PostingsEntry implements Comparable<PostingsEntry>, Serializable {

    public int docID;
    public double score = 0;
    public ArrayList<Integer> offsetList = new ArrayList<>();


    /**
     *  PostingsEntries are compared by their score (only relevant
     *  in ranked retrieval).
     *
     *  The comparison is defined so that entries will be put in 
     *  descending order.
     */

    public void calculate_score(String query_word, double idft, Index index){
        int tf_dt = offsetList.size();
        int doc_len = index.docLengths.get(docID);
        score = tf_dt*idft/doc_len;

    }

    public void sum_score_toentry(double score2){
        score = score + score2;
    }

    public int compareTo( PostingsEntry other ) {
       return Double.compare( other.score, score );
    }


    //
    // YOUR CODE HERE
    //
}

