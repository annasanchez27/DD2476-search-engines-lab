/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Johan Boye, KTH, 2018
 */

package ir;

import java.io.*;
import java.util.*;
import java.nio.charset.*;


/*
 *   Implements an inverted index as a hashtable on disk.
 *
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks.
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentScalableHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    public static final String INDEXDIR = "./index";

    /** The dictionary file name */
    public static final String DICTIONARY_FNAME_FINAL = "dictionaryfinal";

    public static final String DICTIONARY_FNAME = "dictionary";
    /** The dictionary file name */
    public static final String DATA_FNAME_FINAL = "datafinal";

    public static final String DATA_FNAME = "data";
    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    /** The dictionary hash table on disk can fit this many entries. */
    public static final long TABLESIZE = 611953L;

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;


    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile_final;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile_final;

    /** Pointer to the first free memory cell in the data file. */
    long free_final = 0L;

    long free = 0L;

    HashSet<Long> hashing_used = new HashSet<Long>();

    HashSet<Long> hashing_used_final = new HashSet<Long>();

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();

    int MAXIMUM_TOKENS = 75000;

    int tokens_read = 0;

    boolean first = true;
    // ===================================================================

    /**
     *   A helper class representing one entry in the dictionary hashtable.
     */
    public class EntryDataFile {

        public String token;

        public PostingsList postingsList = new PostingsList();


        private PostingsList fromStringtoEntry(String postlist_str){
            String[] first_posting_list = postlist_str.split("\\*");
            token = first_posting_list[0];
            String[] posting_list = first_posting_list[1].split("\n");
            for(int i=0 ; i < posting_list.length ; i++){
                String post = posting_list[i];
                String [] post2 = post.split(":");
                String docID = post2[0];
                String[] offset_stringlist = post2[1].split(",");
                ArrayList<Integer> offsetList = new ArrayList<>();
                for(int j=0; j<offset_stringlist.length; j++){
                    String offset_str = offset_stringlist[j];
                    offsetList.add(Integer.parseInt(offset_str));
                }
                PostingsEntry pentry = new PostingsEntry();
                pentry.docID = Integer.parseInt(docID);
                pentry.offsetList = offsetList;
                postingsList.set(pentry);
            }

            return postingsList;
        }

        public String fromEntryoString(){
            ArrayList<String> final_array = new ArrayList<>();
            for(int i=0;i<postingsList.size();i++){
                PostingsEntry postentry = postingsList.get(i);
                String docID = String.valueOf(postentry.docID);
                ArrayList<String> offset_array = new ArrayList<>();
                for(int j=0;j<postentry.offsetList.size();j++){
                    int offset = postentry.offsetList.get(j);
                    String offset_s = String.valueOf(offset);
                    offset_array.add(offset_s);
                }
                String offset_string = String.join(",",offset_array);
                String entry_string = docID +":"+ offset_string;
                final_array.add(entry_string);

            }
            String final_string = String.join("\n",final_array);
            return token + "*" + final_string;
        }
    }

    public class Entry{
        private long pointer;
        public int length_entrydata;
        public long getPointer(){
            return pointer;
        }
        public void setPointer(long p){
            pointer = p;
        }
    }

    // ==================================================================


    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created.
     */
    public PersistentScalableHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
            dictionaryFile_final = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME_FINAL, "rw" );
            dataFile_final = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME_FINAL, "rw" );
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        try {
            readDocInfo();
        } catch ( FileNotFoundException e ) {
        } catch ( IOException e ) {
            e.printStackTrace();
        }
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */
    int writeData(RandomAccessFile dataFi,String dataString, long ptr ) {
        try {
            dataFi.seek( ptr );
            byte[] data = dataString.getBytes();
            dataFi.write( data );
            return data.length;
        } catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     *  Reads data from the data file
     */
    String readData( long ptr, int size ) {
        try {
            dataFile.seek( ptr );
            byte[] data = new byte[size];
            dataFile.readFully( data );
            return new String(data);
        } catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }


    // ==================================================================
    //
    //  Reading and writing to the dictionary file.

    /*
     *  Writes an entry to the dictionary hash table file.
     *
     *  @param entry The key of this entry is assumed to have a fixed length
     *  @param ptr   The place in the dictionary file to store the entry
     */
    void writeEntry(RandomAccessFile dictionaryFi,Entry entry, long ptr ) {
        try {
            dictionaryFi.seek(ptr);
            dictionaryFi.writeLong(entry.getPointer());
            dictionaryFi.seek(ptr+8);
            dictionaryFi.writeInt(entry.length_entrydata);
        }
        catch (IOException e){};
    }

    /**
     *  Reads an entry from the dictionary file.
     *
     *  @param ptr The place in the dictionary file where to start reading.
     */
    Entry readEntry( long ptr ) {
        Entry en = new Entry();
        try {
            dictionaryFile.seek(ptr);
            long pointer = dictionaryFile.readLong();
            dictionaryFile.seek(ptr+8);
            int num_bytes = dictionaryFile.readInt();
            en.setPointer(pointer);
            en.length_entrydata = num_bytes;

        }
        catch (IOException e){};
        return en;
    }


    // ==================================================================

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    private void writeDocInfo() throws IOException {
        FileOutputStream fout = new FileOutputStream( INDEXDIR + "/docInfo" );
        for (Map.Entry<Integer,String> entry : docNames.entrySet()) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write(docInfoEntry.getBytes());
        }
        fout.close();
    }


    /**
     *  Reads the document names and document lengths from file, and
     *  put them in the appropriate data structures.
     *
     * @throws     IOException  { exception_description }
     */
    private void readDocInfo() throws IOException {
        System.out.println("DOCINFO");

        File file = new File( INDEXDIR + "/docInfo" );
        FileReader freader = new FileReader(file);
        try (BufferedReader br = new BufferedReader(freader)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(";");
                docNames.put(new Integer(data[0]), data[1]);
                docLengths.put(new Integer(data[0]), new Integer(data[2]));
            }
        }
        freader.close();
    }


    /**
     *  Write the index to files.
     */
    public void writeIndex() {
        int collisions = 0;
        try {
            // Write the 'docNames' and 'docLengths' hash maps to a file
            writeDocInfo();
            // Write the dictionary and the postings list
            Iterator indexIterator = index.entrySet().iterator();
            while(indexIterator.hasNext()){
                Map.Entry<String,PostingsList> element = (Map.Entry)indexIterator.next();
                EntryDataFile entrydata = new EntryDataFile();
                entrydata.token = element.getKey();
                entrydata.postingsList = element.getValue();
                String serialized = entrydata.fromEntryoString();
                int readbytes = writeData(dataFile,serialized,free);
                if (readbytes != -1) {
                    Entry e = new Entry();
                    e.setPointer(free);
                    e.length_entrydata = readbytes;
                    free += readbytes + 1;
                    Long hash = hashCode(entrydata.token);
                    while(hashing_used.contains(hash)){
                        hash += 12;
                        collisions ++;
                    }
                    hashing_used.add(hash);
                    writeEntry(dictionaryFile,e,hash); //SHOULD be 12
                }

            }

        } catch ( IOException e ) {
            e.printStackTrace();
        }
        System.err.println( collisions + " collisions." );
    }
    public PostingsList getPostingsMemory( String token ) {
        PostingsList post_list;
        post_list = index.get(token);
        return post_list;
    }

    // ==================================================================


    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
        long hash = hashCode(token);
        String token_found = "";
        String s = "";
        while(!token.equals(token_found)){
            Entry e = readEntry(hash);
            long pointer = e.getPointer();
            int bytes_data = e.length_entrydata;
            s = readData(pointer,bytes_data);
            token_found = fromStringtoToken(s);
            hash = hash + 12;
        }
        EntryDataFile entry = new EntryDataFile();
        PostingsList plist = entry.fromStringtoEntry(s);
        return plist;
    }


    public String fromStringtoToken(String s){
        return s.split("\\*")[0];
    }

    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
        if(tokens_read<MAXIMUM_TOKENS){
            PostingsList post_list = getPostingsMemory(token);
            PostingsEntry post_entry = new PostingsEntry();
            post_entry.docID = docID;
            post_entry.offsetList.add(offset);
            if (post_list == null) {
                PostingsList p_list = new PostingsList();
                p_list.set(post_entry);
                index.put(token, p_list);
                tokens_read++;
            } else {
                post_list.set(post_entry);
            }

        }
        else {
            cleanup();
            insert(token,docID,offset);

        }

    }
    public void writePostingList(PostingsList p1, String token) {
        int collisions = 0;
        EntryDataFile entrydata = new EntryDataFile();
        entrydata.token = token;
        entrydata.postingsList = p1;
        String serialized = entrydata.fromEntryoString();
        int readbytes = writeData(dataFile_final,serialized,free);
        if (readbytes != -1) {
            Entry e = new Entry();
            e.setPointer(free);
            e.length_entrydata = readbytes;
            free += readbytes + 1;
            Long hash = hashCode(entrydata.token);
            while (hashing_used_final.contains(hash)) {
                hash += 12;
                collisions++;
            }
            hashing_used_final.add(hash);
            writeEntry(dictionaryFile_final, e, hash);
        }
    }


    public void merge() {
        int collisions = 0;
        index.clear();
        free = 0L;
        tokens_read = 0;
        long position = 0;
        while (position < TABLESIZE) {
            Entry e = readEntry(position);
            position += 12;
            long pointer_datafile = e.getPointer();
            int entry_length = e.length_entrydata;
            String data = readData(pointer_datafile, entry_length);
            String token = fromStringtoToken(data);
            if (index.containsKey(token)) {
                PostingsList plist1 = index.get(token);
                EntryDataFile entrydata = new EntryDataFile();
                PostingsList plist2 = entrydata.fromStringtoEntry(data);
                PostingsList plist3 = intersection(plist1, plist2);
                index.remove(token);
                writePostingList(plist3,token);
            } else {
                int readbytes = writeData(dataFile_final, data, entry_length);
                if (readbytes != -1) {
                    Entry en = new Entry();
                    en.setPointer(free);
                    en.length_entrydata = readbytes;
                    free_final += readbytes + 1;
                    Long hash = hashCode(token);
                    while (hashing_used_final.contains(hash)) {
                        hash += 12;
                        collisions++;
                    }
                    hashing_used_final.add(hash);
                    writeEntry(dictionaryFile_final, en, hash);

                }
            }
        }
        // Iterate over the remaining index
        Iterator indexIterator = index.entrySet().iterator();
        while(indexIterator.hasNext()){
            Map.Entry<String,PostingsList> element = (Map.Entry)indexIterator.next();
            EntryDataFile entrydata = new EntryDataFile();
            entrydata.token = element.getKey();
            entrydata.postingsList = element.getValue();
            String serialized = entrydata.fromEntryoString();
            int readbytes = writeData(dataFile_final,serialized,free);
            if (readbytes != -1) {
                Entry ee = new Entry();
                ee.setPointer(free);
                ee.length_entrydata = readbytes;
                free += readbytes + 1;
                Long hash = hashCode(entrydata.token);
                while(hashing_used_final.contains(hash)){
                    hash += 12;
                    collisions ++;
                }
                hashing_used_final.add(hash);
                writeEntry(dictionaryFile_final,ee,hash); //SHOULD be 12
            }

        }

    }

    public PostingsList intersection(PostingsList p1, PostingsList p2){
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
                    ArrayList<Integer> result = merge_offset(offsetList1,offsetList2);
                    PostingsEntry postr = new PostingsEntry();
                    postr.docID = pp1.docID;
                    postr.offsetList = result;
                    if(result.size()>0){
                        p3.set(postr);
                    }
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
        }
        return p3;
    }
    public ArrayList<Integer> merge_offset(ArrayList<Integer> offsetList1, ArrayList<Integer> offsetList2){
            ArrayList<Integer> p3 = new ArrayList<Integer>();
            int  l1 = offsetList1.size();
            int l2 = offsetList2.size();
            int i1 = 0;
            int i2 = 0;
            if (l1>0 && l2>0){
                while (i1 < l1 && i2 < l2) {
                    int num1 = offsetList1.get(i1);
                    int num2 = offsetList2.get(i2);
                    if (num1==num2) {
                        p3.add(num2);
                        i1 = i1 + 1;
                        i2 = i2 + 1;

                    } else if (num1 < num2) {
                        p3.add(num1);
                        i1 = i1 + 1;
                    } else {
                        p3.add(num2);
                        i2 = i2 + 1;
                    }
                }
            }
            //System.out.println("offset list found");
            return p3;



        }


    private long hashCode(String word) {
        long hashed = word.hashCode() & 0xfffffff;
        return  hashed % TABLESIZE *12;
    }

    /**
     *  Write index to file after indexing is done.
     */
    public void cleanup() {
        if(first){
            first = false;
            writeIndex();
            index.clear();
            tokens_read = 0;
            free = 0L;
            System.err.println( index.keySet().size() + " unique words" );
            System.err.print( "Writing index to disk..." );
            System.err.println( "done!" );
        }else{
            merge();
            System.out.println("Merged");
            dataFile = dataFile_final;
            dictionaryFile = dictionaryFile_final;
            try {
                dataFile_final.setLength(0);
                dictionaryFile_final.setLength(0);
            }
            catch(IOException e){}
            hashing_used.clear();

        }


    }
}
