/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;

    /** The k-gram index to be searched by this Searcher */
    KGramIndex kgIndex;
    
    /** Constructor */
    public Searcher( Index index, KGramIndex kgIndex ) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search( Query query, QueryType queryType, RankingType rankingType ) {
        ArrayList<Query.QueryTerm> list = query.queryterm;
        ArrayList<String> stringquery = new ArrayList<String>();
        for (int i = 0; i < list.size(); i++) {
            Query.QueryTerm q = list.get(i);
            stringquery.add(q.getString());

        }
        if(queryType==QueryType.INTERSECTION_QUERY){
            PostingsList p1 = index.getPostings(stringquery.get(0));
            for(int i=1;i<stringquery.size();i++){
                PostingsList p2 = index.getPostings(stringquery.get(i));
                p1 = interesection(p1,p2);
            }
            return p1;

        }
        else {
            return index.getPostings(stringquery.get(0));
        }

    }


    public PostingsList interesection(PostingsList p1, PostingsList p2){
        PostingsList p3 = new PostingsList();
        int  l1 = p1.size();
        int l2 = p2.size();
        int i1 = 0;
        int i2 = 0;
        if (l1>0 && l2>0){
            while (i1 < l1 && i2 < l2) {
                PostingsEntry pp1 = p1.get(i1);
                PostingsEntry pp2 = p2.get(i2);
                if (pp1.docID == pp2.docID) {
                    p3.set(pp1);
                    i1 = i1 + 1;
                    i2 = i2 + 1;

                } else if (pp1.docID < pp2.docID) {
                    i1 = i1 + 1;
                } else {
                    i2 = i2 + 1;
                }
            }
        }
        return p3;
    }







}