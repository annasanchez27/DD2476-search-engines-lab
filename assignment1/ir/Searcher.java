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
            return this.search_engine_intersection(stringquery);
        }
        if(queryType==QueryType.PHRASE_QUERY){
            return this.search_engine_phrase_query(stringquery);
        }


        if(queryType==QueryType.RANKED_QUERY){
            return this.search_engine_ranked_query(query,stringquery,rankingType,normtype);
            }
        else {
            return index.getPostings(stringquery.get(0));
        }
    }

    public PostingsList merge(PostingsList p1, PostingsList p2) {
        PostingsList result = new PostingsList();
        int p1_index = 0, p2_index = 0;
        int p1_length = p1.size(), p2_length = p2.size();

        while (p1_index < p1_length & p2_index < p2_length){
            int p1_docID = p1.get(p1_index).docID,  p2_docID = p2.get(p2_index).docID;
            if (p1_docID  == p2_docID) {
                PostingsEntry newEntry = new PostingsEntry();
                newEntry.score = p1.get(p1_index).score + p2.get(p2_index).score;
                newEntry.docID = p1_docID;
                newEntry.offsetList = merge_offsets(p1.get(p1_index).offsetList, p2.get(p2_index).offsetList);
                result.set(newEntry);
                p1_index++;
                p2_index++;
            } else if (p1_docID < p2_docID) {
                result.set(p1.get(p1_index));
                p1_index++;
            } else {
                result.set(p2.get(p2_index));
                p2_index++;
            }
        }

        while (p1_index < p1_length){
            result.set(p1.get(p1_index));
            p1_index++;
        }

        while (p2_index < p2_length){
            result.set(p2.get(p2_index));
            p2_index++;
        }

        return result;
    }


    public PostingsList union(PostingsList p1, PostingsList p2){
        PostingsList p3 = new PostingsList();
        int  l1 = p1.size();
        int l2 = p2.size();
        int i1 = 0;
        int i2 = 0;
        //if (l1>0 && l2>0){
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
        //}
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

    public ArrayList<Integer> merge_offsets(ArrayList<Integer> p1, ArrayList<Integer> p2){
        ArrayList<Integer> p3 = new ArrayList<Integer>();
        int  l1 = p1.size();
        int l2 = p2.size();
        int i1 = 0;
        int i2 = 0;
        //if (l1>0 && l2>0){
        while (i1 < l1 && i2 < l2) {
            Integer pp1 = p1.get(i1);
            Integer pp2 = p2.get(i2);
            if (pp1 == pp2) {
                p3.add(pp1);
                i1 = i1 + 1;
                i2 = i2 + 1;

            } else if (pp1< pp2) {
                p3.add(pp1);
                i1 = i1 + 1;
            } else {
                p3.add(pp2);
                i2 = i2 + 1;
            }
        }
        if (i1 < l1) {
            while(i1<l1){
                Integer pp1 = p1.get(i1);
                p3.add(pp1);
                i1 = i1 + 1;
            }
        } else if (i2 < l2) {
            while(i2<l2){
                Integer pp2 = p2.get(i2);
                p3.add(pp2);
                i2 = i2 + 1;
            }
        }
        //}
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

    public PostingsList search_engine_intersection(ArrayList<String> stringquery){
        PostingsList plist = new PostingsList();
        for (int i = 0; i < stringquery.size(); i++) {
            ArrayList<String> words = kgIndex.resolve_string_wildcard(stringquery.get(i));
            PostingsList posting_union = index.getPostings(words.get(0));
            if(posting_union==null){
                return null;
            }
            for(int l=1; l<words.size(); l++) {
                PostingsList p1 = index.getPostings(words.get(l));
                if(p1==null){
                    return null;
                }
                posting_union = union(p1,posting_union);
            }
            if(plist.size()==0){
                plist = posting_union;
            }else {
                plist = interesection(plist, posting_union);
            }
        }

        return plist;
    }

    public PostingsList search_engine_phrase_query(ArrayList<String> stringquery){
        PostingsList plist = null;
        for (int i = 0; i < stringquery.size(); i++) {
            ArrayList<String> words = kgIndex.resolve_string_wildcard(stringquery.get(i));
            PostingsList posting_union = index.getPostings(words.get(0));
            for(int l=1; l<words.size(); l++) {
                PostingsList p1 = index.getPostings(words.get(l));
                posting_union = merge(p1,posting_union);

            }
            if(plist==null){
                plist = posting_union;
            }else {
                System.out.println("Arguments");
                System.out.println(plist.size());
                System.out.println(posting_union.size());
                plist = interesection_phrasequery(plist, posting_union);
                System.out.println("Solution");
                System.out.println(plist.size());
            }
        }
        return plist;
    }

    public PostingsList search_engine_ranked_query(Query query,ArrayList<String> stringquery,RankingType rankingType, NormalizationType normtype){
        if(rankingType == RankingType.HITS) {
            HITSRanker hr = new HITSRanker("pagerank/linksDavis.txt",
                    "pagerank/davisTitles.txt",this.index);
            PostingsList p1 = hr.rank(stringquery);
            p1.sort_posting();
            return p1;
        }
        else {
            PostingsList p1 = new PostingsList();
            PostingsList result = new PostingsList();
            for (int i = 0; i < stringquery.size(); i++) {
                ArrayList<String> words = kgIndex.resolve_string_wildcard(query.queryterm.get(i).term);
                for(int q=0 ; q<words.size(); q++){
                    PostingsList p2 = index.getPostings(words.get(q));
                    //if(p2==null){
                        //continue;
                   // }
                    double weight2 = query.queryterm.get(i).weight;
                    double idft2 = p2.calculate_idf(this.index);
                    for (int j = 0; j < p2.size(); j++) {
                        PostingsEntry entry = p2.get(j);
                        double eucl_length_doc = 0;
                        if (euclidian_length.containsKey(entry.docID)) {
                            eucl_length_doc = euclidian_length.get(entry.docID);
                        }
                        if (rankingType == RankingType.TF_IDF) {
                            entry.calculate_score(idft2, index, normtype, eucl_length_doc,weight2);
                        }
                        if (rankingType == RankingType.PAGERANK) {
                            entry.score = this.ranking_hash.get(entry.docID);
                        }
                        if (rankingType == RankingType.COMBINATION) {
                            entry.calculate_score(idft2, index, normtype, eucl_length_doc,weight2);
                            double sum = entry.score + this.ranking_hash.get(entry.docID);
                            entry.score = 0.7 * entry.score/sum + 0.3 * this.ranking_hash.get(entry.docID)/sum;
                        }
                    }
                    if(result.size()==0){
                        result = p2;
                    }else {
                        result = union(p2, result);
                    }
                }

            }
            result.sort_posting();

            return result;
        }
    }


}