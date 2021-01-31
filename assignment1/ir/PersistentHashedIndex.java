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
public class PersistentHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    public static final String INDEXDIR = "./index";

    /** The dictionary file name */
    public static final String DICTIONARY_FNAME = "dictionary";

    /** The dictionary file name */
    public static final String DATA_FNAME = "data";

    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    /** The dictionary hash table on disk can fit this many entries. */
    public static final long TABLESIZE = 611953L;

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** Pointer to the first free memory cell in the data file. */
    long free = 0L;

    HashSet<Long> hashing_used = new HashSet<Long>();

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();


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
    public PersistentHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
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
    int writeData( String dataString, long ptr ) {
        try {
            dataFile.seek( ptr ); 
            byte[] data = dataString.getBytes();
            dataFile.write( data );
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
    void writeEntry( Entry entry, long ptr ) {
        try {
            dictionaryFile.seek(ptr);
            dictionaryFile.writeLong(entry.getPointer());
            dictionaryFile.seek(ptr+8);
            dictionaryFile.writeInt(entry.length_entrydata);
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
                int readbytes = writeData(serialized,free);
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
                    writeEntry(e,hash); //SHOULD be 12
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
        PostingsList post_list = getPostingsMemory(token);
        PostingsEntry post_entry = new PostingsEntry();
        post_entry.docID = docID;
        post_entry.offsetList.add(offset);
        if (post_list == null){
            PostingsList p_list = new PostingsList();
            p_list.set(post_entry);
            index.put(token,p_list);
        }else{
            post_list.set(post_entry);
        }
    }




    private long hashCode(String word) {
        long hashed = word.hashCode() & 0xfffffff;
        return  hashed % TABLESIZE *12;
    }

    /**
     *  Write index to file after indexing is done.
     */
    public void cleanup() {
        System.err.println( index.keySet().size() + " unique words" );
        System.err.print( "Writing index to disk..." );
        writeIndex();
        System.err.println( "done!" );
    }
}
