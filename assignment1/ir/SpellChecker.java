/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.util.*;


public class SpellChecker {
    /** The regular inverted index to be used by the spell checker */
    Index index;

    /** K-gram index to be used by the spell checker */
    KGramIndex kgIndex;

    /** The auxiliary class for containing the value of your ranking function for a token */
    class KGramStat implements Comparable {
        double score;
        String token;

        KGramStat(String token, double score) {
            this.token = token;
            this.score = score;
        }

        public String getToken() {
            return token;
        }

        public int compareTo(Object other) {
            if (this.score == ((KGramStat)other).score) return 0;
            return this.score < ((KGramStat)other).score ? -1 : 1;
        }

        public String toString() {
            return token + ";" + score;
        }
    }

    /**
     * The threshold for Jaccard coefficient; a candidate spelling
     * correction should pass the threshold in order to be accepted
     */
    private static final double JACCARD_THRESHOLD = 0.4;


    /**
      * The threshold for edit distance for a candidate spelling
      * correction to be accepted.
      */
    private static final int MAX_EDIT_DISTANCE = 2;


    public SpellChecker(Index index, KGramIndex kgIndex) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     *  Computes the Jaccard coefficient for two sets A and B, where the size of set A is 
     *  <code>szA</code>, the size of set B is <code>szB</code> and the intersection 
     *  of the two sets contains <code>intersection</code> elements.
     */
    private double jaccard(int szA, int szB, int intersection) {
        double result = (double) intersection/(szA+szB-intersection);
        return result;
    }

    /**
     * Computing Levenshtein edit distance using dynamic programming.
     * Allowed operations are:
     *      => insert (cost 1)
     *      => delete (cost 1)
     *      => substitute (cost 2)
     */
    private int editDistance(String x, String y) {
        if (x.isEmpty()) {
            return y.length();
        }
        if (y.isEmpty()) {
            return x.length();
        }
        int substitution = editDistance(x.substring(1), y.substring(1))
                + costOfSubstitution(x.charAt(0), y.charAt(0));
        int insertion = editDistance(x, y.substring(1)) + 1;
        int deletion = editDistance(x.substring(1), y) + 1;

        return min(substitution, insertion, deletion);
    }
    public static int costOfSubstitution(char a, char b) {
        return a == b ? 0 : 2;
    }

    public static int min(int... numbers) {
        return Arrays.stream(numbers)
                .min().orElse(Integer.MAX_VALUE);
    }

    /**
     *  Checks spelling of all terms in <code>query</code> and returns up to
     *  <code>limit</code> ranked suggestions for spelling correction.
     */
    public String[] check(Query query, int limit) {
        // 1st step: for all the terms in query, we are going to check the ones that are misspelled
        String[] suggestions = null;
        ArrayList<Query.QueryTerm> list_query = query.queryterm;
        for(int i=0; i<list_query.size(); i++){
            String word_query = list_query.get(i).term;
            PostingsList plist = this.index.getPostings(word_query);
            if(plist == null){
                //misspelled word
                suggestions = get_suggestions_token(word_query);
            }
        }
        return suggestions;
    }
    public String [] get_suggestions_token(String word_query){
        //This is a misspelled word
        //1. get all the k-grams
        ArrayList<String> list_kgrams = this.kgIndex.list_of_kgrams(word_query);
        //2. for all the different k-grams get the postings list and calculate jaccard.
        Set<String> result = new HashSet<>();
        for(int i=0; i<list_kgrams.size(); i++){
            String kgr_str = list_kgrams.get(i);
            List<KGramPostingsEntry> entry = kgIndex.index.get(kgr_str);
            for(int j=0; j< entry.size(); j++) {
                KGramPostingsEntry concrete_word = entry.get(j);
                String term = kgIndex.id2term.get(concrete_word.tokenID);
                ArrayList<String> kgram_b = kgIndex.list_of_kgrams(term);
                int num_inters = intersect_kgrams(list_kgrams,kgram_b);
                //List<KGramPostingsEntry> intersect_kgram = kgIndex.intersect(kgram_b,list_kgrams);
                double num = jaccard(list_kgrams.size(), kgram_b.size(),num_inters);
                if(num>=JACCARD_THRESHOLD){
                    result.add(term);
                }
            }
        }

        //4. for all possible we need to calculate distance
        HashSet<String> filtered_distance = calculate_distance_forall(word_query,result);

        String [] filtered_everything = new String[filtered_distance.size()];
        //5. Get the strings
        Iterator<String> it = filtered_distance.iterator();
        int j = 0;
        while(it.hasNext()){
            String entry = it.next();
            filtered_everything[j] = entry;
            j++;
        }

        return filtered_everything;
    }


    private int intersect_kgrams(ArrayList<String> p1, ArrayList<String> p2){
        int count = 0;
       for(int i=0; i<p1.size(); i++){
           String term = p1.get(i);
           if(p2.contains(term)){
               count ++;
           }

       }
       return count;
    }


    private HashSet<String> calculate_distance_forall(String word_query,Set<String> words){
        HashSet<String> result = new HashSet<>();
        Iterator<String> it = words.iterator();
        while(it.hasNext()){
            String entry = it.next();
            double num = editDistance(entry,word_query);
            if(num<=MAX_EDIT_DISTANCE){
                result.add(entry);
            }
        }
        return result;
    }




    /**
     *  Merging ranked candidate spelling corrections for all query terms available in
     *  <code>qCorrections</code> into one final merging of query phrases. Returns up
     *  to <code>limit</code> corrected phrases.
     */
    private List<KGramStat> mergeCorrections(List<List<KGramStat>> qCorrections, int limit) {
        //
        // YOUR CODE HERE
        //
        return null;
    }
}
