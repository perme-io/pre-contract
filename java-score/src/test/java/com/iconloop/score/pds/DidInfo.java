package com.iconloop.score.pds;

import score.ObjectReader;
import score.ObjectWriter;


public class DidInfo {
    private final String did;
    private String[] kids;
    private String[] publicKeys;

    public DidInfo(String did, String[] kids, String[] publicKeys) {
        this.did = did;
        if (kids == null) {
            this.kids = new String[0];
        } else {
            this.kids = kids;
        }

        if (publicKeys == null) {
            this.publicKeys = new String[0];
        } else {
            this.publicKeys = publicKeys;
        }
    }

    public void addPublicKey(String kid, String publicKey) {
        String[] newKids = new String[this.kids.length + 1];
        String[] newPublicKeys = new String[this.publicKeys.length + 1];

        System.arraycopy(this.kids, 0, newKids, 0, this.kids.length);
        System.arraycopy(this.publicKeys, 0, newPublicKeys, 0, this.publicKeys.length);

        newKids[this.kids.length] = kid;
        newPublicKeys[this.publicKeys.length] = publicKey;

        this.kids = newKids;
        this.publicKeys = newPublicKeys;
    }

    public String getPublicKey(String kid) {
        for (int i = 0; i < this.kids.length; i++) {
            if (this.kids[i].equals(kid)) {
                return this.publicKeys[i];
            }
        }

        return null;
    }

    public static void writeObject(ObjectWriter w, DidInfo t) {
        String kidsString = Helper.StringListToJsonString(t.kids);
        String publicKeysString = Helper.StringListToJsonString(t.publicKeys);
        w.beginList(3);
        w.writeNullable(t.did);
        w.writeNullable(kidsString);
        w.writeNullable(publicKeysString);
        w.end();
    }

    public static DidInfo readObject(ObjectReader r) {
        r.beginList();
        DidInfo t = new DidInfo(
                r.readNullable(String.class),
                Helper.JsonStringToStringList("kids", r.readNullable(String.class)),
                Helper.JsonStringToStringList("publicKeys", r.readNullable(String.class)));
        r.end();
        return t;
    }
}
