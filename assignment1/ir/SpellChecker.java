/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.util.*;
import java.util.stream.IntStream;


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
    private int editDistance_seb(String s1, String s2) {
        s1 = "#" + s1;
        s2 = "#" + s2;
        int[] scores = IntStream.range(0, s1.length()).toArray();
        for (int i = 0; i < s2.length(); i++){
            if (i == 0) {
                continue;
            }
            int[] newScores = new int[s1.length()];
            for (int  j = 0; j < s1.length();j++){
                if (j == 0){
                    newScores[j] = i;
                } else{
                    int diagonal = scores[j-1];
                    int left = newScores[j-1] + 1;
                    int up = scores[j] + 1;
                    String currLetter1 = s1.substring(j, j + 1);
                    String currLetter2 = s2.substring(i, i +1);
                    if (!currLetter1.equals(currLetter2)) diagonal += 2;
                    newScores[j] = Math.min(diagonal, Math.min(left, up));
                }
            }
            scores = newScores;
        }
        return scores[scores.length - 1];
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
                suggestions = order_list(suggestions);
            }
        }
        return suggestions;
    }

    private String [] order_list(String [] suggestions){
        List<KGramStat> list_kgr = new ArrayList<>();
        for(int i=0; i<suggestions.length; i++){
            KGramStat kgr = new KGramStat(suggestions[i],index.getPostings(suggestions[i]).size());
            list_kgr.add(kgr);
        }
        Collections.sort(list_kgr, Collections.reverseOrder());
        String [] result = new String[list_kgr.size()];
        for(int j=0; j<list_kgr.size(); j++){
            result[j] = list_kgr.get(j).token;
        }

        return result;
    }

    public String [] get_suggestions_token(String word_query){
        //This is a misspelled word
        //1. get all the k-grams

        ArrayList<String> list_kgrams = this.kgIndex.list_of_kgrams(word_query);
        HashMap<String,Integer> hash_inters = new HashMap<>();
        Set<String> result = new HashSet<>();
        for(int i=0; i<list_kgrams.size(); i++){
            String kgr_str = list_kgrams.get(i);
            List<KGramPostingsEntry> entry = kgIndex.index.get(kgr_str);
            for(int j=0; j< entry.size(); j++) {
                KGramPostingsEntry concrete_word = entry.get(j);
                String term = kgIndex.id2term.get(concrete_word.tokenID);
                if(hash_inters.containsKey(term)){
                    Integer val = hash_inters.get(term);
                    hash_inters.put(term,val+1);
                }else{
                    hash_inters.put(term, 1);
                }
            }
        }



        Iterator iter = hash_inters.entrySet().iterator();
        int len_a = list_kgrams.size();
        int ka = kgIndex.getK();
        while(iter.hasNext()) {
            Map.Entry<String,Integer> pair = (Map.Entry)iter.next();
            String s = pair.getKey();
            int inters_value = pair.getValue();
            double num = jaccard(len_a,s.length() + 3 -ka,inters_value);
            if (num >= JACCARD_THRESHOLD) {
                //double startTime =  System.currentTimeMillis();
                double editDist = editDistance_seb(s,word_query);
                //double elapsedTime = System.currentTimeMillis() - startTime;
                //System.err.println("It took " + elapsedTime / 1000.0 + "s to check " + s);
                if(editDist<=MAX_EDIT_DISTANCE){
                    result.add(s);
                }
            }
        }
        String [] filtered_everything = new String[result.size()];
        result.toArray(filtered_everything);

        return filtered_everything;
    }







    /**
     *  Merging ranked candidate spelling corrections for all query terms available in
     *  <code>qCorrections</code> into one final merging of query phrases. Returns up
     *  to <code>limit</code> corrected phrases.
     */
    private List<KGramStat> mergeCorrections(List<List<KGramStat>> qCorrections, int limit) {

        return null;
    }
}
