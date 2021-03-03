/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.nio.charset.*;
import java.io.*;


/**
 *  A class for representing a query as a list of words, each of which has
 *  an associated weight.
 */
public class Query {

    /**
     * Help class to represent one query term, with its associated weight.
     */
    class QueryTerm {
        String term;
        double weight;

        QueryTerm(String t, double w) {
            term = t;
            weight = w;
        }

        public String getString() {
            return term;
        }
    }

    /**
     * Representation of the query as a list of terms with associated weights.
     * In assignments 1 and 2, the weight of each term will always be 1.
     */
    public ArrayList<QueryTerm> queryterm = new ArrayList<QueryTerm>();

    /**
     * Relevance feedback constant alpha (= weight of original query terms).
     * Should be between 0 and 1.
     * (only used in assignment 3).
     */
    double alpha = 0.2;

    /**
     * Relevance feedback constant beta (= weight of query terms obtained by
     * feedback from the user).
     * (only used in assignment 3).
     */
    double beta = 1 - alpha;


    /**
     * Creates a new empty Query
     */
    public Query() {
    }


    /**
     * Creates a new Query from a string of words
     */
    public Query(String queryString) {
        StringTokenizer tok = new StringTokenizer(queryString);
        while (tok.hasMoreTokens()) {
            queryterm.add(new QueryTerm(tok.nextToken(), 1.0));
        }
    }


    /**
     * Returns the number of terms
     */
    public int size() {
        return queryterm.size();
    }


    /**
     * Returns the Manhattan query length
     */
    public double length() {
        double len = 0;
        for (QueryTerm t : queryterm) {
            len += t.weight;
        }
        return len;
    }


    /**
     * Returns a copy of the Query
     */
    public Query copy() {
        Query queryCopy = new Query();
        for (QueryTerm t : queryterm) {
            queryCopy.queryterm.add(new QueryTerm(t.term, t.weight));
        }
        return queryCopy;
    }


    /**
     * Expands the Query using Relevance Feedback
     *
     * @param results       The results of the previous query.
     * @param docIsRelevant A boolean array representing which query results the user deemed relevant.
     * @param engine        The search engine object
     */
    public void relevanceFeedback(PostingsList results, boolean[] docIsRelevant, Engine engine) {

        // 1. For every relevant document get the relevant tokens
        HashMap<String, Double> query_count = new HashMap<>();
        int num_relevant_documents = 0;

        for (boolean value : docIsRelevant) {
            if (value) {
                num_relevant_documents++;
            }
        }

        if(num_relevant_documents==0){
            return ;
        }

        for (int i = 0; i < docIsRelevant.length; i++) {
            boolean value = docIsRelevant[i];
            if (value) {
                PostingsEntry entry = results.get(i);
                String path_name = engine.index.docNames.get(entry.docID);
                if(path_name.equals("/Users/annasanchezespunyes/Documents/KTH/Search_Engines/davisWiki/Math.f")){
                    path_name = "/Users/annasanchezespunyes/Documents/KTH/Search_Engines/davisWiki/Mathematics.f";
                }
                System.out.println(path_name);
                process_file(path_name,engine,query_count,num_relevant_documents);
            }
        }
        for(QueryTerm query_entry: queryterm){
            if(query_count.containsKey(query_entry.term)) {
                query_count.put(query_entry.term, query_count.get(query_entry.term) + alpha);
            } else {
                query_count.put(query_entry.term, alpha);
            }
        }

        //ArrayList<QueryTerm> centroid_query = new ArrayList<>();
        queryterm.clear();
        for (HashMap.Entry<String, Double> entry : query_count.entrySet()) {
            QueryTerm query = new QueryTerm(entry.getKey(), entry.getValue());
            queryterm.add(query);
        }
    }


    public void process_file(String path_name, Engine engine, HashMap<String, Double> query_count, Integer num_relevant_documents) {
        try {
            Reader reader = new InputStreamReader(new FileInputStream(path_name), StandardCharsets.UTF_8);
            Tokenizer tok = new Tokenizer(reader, true, false, true, engine.patterns_file);
            while (tok.hasMoreTokens()) {
                String token = tok.nextToken();
                if(query_count.containsKey(token)) {
                    double num = query_count.get(token);
                    query_count.put(token,num + (1./num_relevant_documents)*beta);
                }else {
                    query_count.put(token,(1./num_relevant_documents)*beta);
                }

            }
            reader.close();
        } catch (IOException e) {
            System.err.println("Warning: IOException during indexing.");
        }
    }
}










