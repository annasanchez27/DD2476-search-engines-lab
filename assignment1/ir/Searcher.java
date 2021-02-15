/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;

    /** The k-gram index to be searched by this Searcher */
    KGramIndex kgIndex;

    HashMap<Integer,Double> ranking_hash;

    HashMap<Integer,Double> euclidian_length;
    
    /** Constructor */
    public Searcher(Index index, KGramIndex kgIndex, HashMap<Integer,Double> ranking_hash, HashMap<Integer,Double> euclidian_length ) {
        this.index = index;
        this.kgIndex = kgIndex;
        this.ranking_hash = ranking_hash;
        this.euclidian_length = euclidian_length;

    }

    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search( Query query, QueryType queryType, RankingType rankingType, NormalizationType normtype) {
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
        if(queryType==QueryType.PHRASE_QUERY){
            //System.out.println("IN phrase query");
            PostingsList p1 = index.getPostings(stringquery.get(0));
            for(int i=1;i<stringquery.size();i++){
                PostingsList p2 = index.getPostings(stringquery.get(i));
                p1 = interesection_phrasequery(p1,p2);
                //System.out.println("Intersection done");
            }
            //System.out.println("Going to the return");
            return p1;
        }


        if(queryType==QueryType.RANKED_QUERY){
            if(rankingType == RankingType.HITS) {
                HITSRanker hr = new HITSRanker("pagerank/linksDavis.txt",
                        "pagerank/davisTitles.txt",this.index);
                PostingsList p1 = hr.rank(stringquery);
                p1.sort_posting();
                return p1;
            }
            else {
                PostingsList p1 = index.getPostings(stringquery.get(0));
                double idft = p1.calculate_idf(this.index);
                for (int i = 0; i < p1.size(); i++) {
                    PostingsEntry entry = p1.get(i);
                    double eucl_length_doc = 0;
                    if (euclidian_length.containsKey(entry.docID)) {
                        eucl_length_doc = euclidian_length.get(entry.docID);
                    }
                    if (rankingType == RankingType.TF_IDF) {
                        entry.calculate_score(idft, index, normtype, eucl_length_doc);
                    }
                    if (rankingType == RankingType.PAGERANK) {
                        entry.score = this.ranking_hash.get(entry.docID);

                    }
                    if (rankingType == RankingType.COMBINATION) {
                        entry.calculate_score(idft, index, normtype, eucl_length_doc);
                        entry.score = 0.2 * entry.score + 0.8 * this.ranking_hash.get(entry.docID);
                    }

                }


                for (int i = 1; i < stringquery.size(); i++) {
                    PostingsList p2 = index.getPostings(stringquery.get(i));
                    double idft2 = p2.calculate_idf(this.index);
                    for (int j = 0; j < p2.size(); j++) {
                        PostingsEntry entry = p2.get(j);
                        double eucl_length_doc = 0;
                        if (euclidian_length.containsKey(entry.docID)) {
                            eucl_length_doc = euclidian_length.get(entry.docID);
                        }
                        if (rankingType == RankingType.TF_IDF) {
                            entry.calculate_score(idft2, index, normtype, eucl_length_doc);
                        }
                        if (rankingType == RankingType.PAGERANK) {
                            entry.score = this.ranking_hash.get(entry.docID);
                        }
                        if (rankingType == RankingType.COMBINATION) {
                            entry.calculate_score(idft2, index, normtype, eucl_length_doc);
                            entry.score = 0.2 * entry.score + 0.8 * this.ranking_hash.get(entry.docID);
                        }

                    }
                    p1 = union(p1, p2);
                }
                p1.sort_posting();
                return p1;
            }
            }

        else {
            return index.getPostings(stringquery.get(0));
        }
    }



    public PostingsList union(PostingsList p1, PostingsList p2){
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
                    pp1.sum_score_toentry(pp2.score);
                    p3.set(pp1);
                    i1 = i1 + 1;
                    i2 = i2 + 1;

                } else if (pp1.docID < pp2.docID) {
                    p3.set(pp1);
                    i1 = i1 + 1;
                } else {
                    p3.set(pp2);
                    i2 = i2 + 1;
                }
            }
            if (i1 < l1) {
                while(i1<l1){
                    PostingsEntry pp1 = p1.get(i1);
                    p3.set(pp1);
                    i1 = i1 + 1;
                }
            } else if (i2 < l2) {
                while(i2<l2){
                    PostingsEntry pp2 = p2.get(i2);
                    p3.set(pp2);
                    i2 = i2 + 1;
                }
            }
        }
        return p3;
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

    public PostingsList interesection_phrasequery(PostingsList p1, PostingsList p2){
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
                    ArrayList<Integer> offsetList1 = pp1.offsetList;
                    ArrayList<Integer> offsetList2 = pp2.offsetList;
                    ArrayList<Integer> result = find_matchingoffset(offsetList1,offsetList2);
                    PostingsEntry postr = new PostingsEntry();
                    postr.docID = pp1.docID;
                    postr.offsetList = result;
                    if(result.size()>0){
                        p3.set(postr);
                    }

                    //We are going to iterate over the offset list
                    //If we find that offset1 is equal to offset2-1 then we:
                        //Create a PostingsList (no need, we already have p3)
                        //Create a PostingEntry with a DocID
                        //Add the offset2 in the list of this PostingEntry
                        //Add the PostingEntry in p3
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

    public ArrayList<Integer> find_matchingoffset(ArrayList<Integer> offsetList1, ArrayList<Integer> offsetList2){
        //System.out.println("Starting finding match");
        ArrayList<Integer> p3 = new ArrayList<Integer>();
        int  l1 = offsetList1.size();
        int l2 = offsetList2.size();
        int i1 = 0;
        int i2 = 0;
        if (l1>0 && l2>0){
            while (i1 < l1 && i2 < l2) {
                int num1 = offsetList1.get(i1);
                int num2 = offsetList2.get(i2);
                if (num1 == num2-1) {
                    p3.add(num2);
                    i1 = i1 + 1;
                    i2 = i2 + 1;

                } else if (num1 < num2-1) {
                    i1 = i1 + 1;
                } else {
                    i2 = i2 + 1;
                }
            }
        }
        //System.out.println("offset list found");
        return p3;
    }




}