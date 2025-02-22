package org.bouncycastle.asn1;

import java.io.IOException;

/**
 * ASN.1 TaggedObject - in ASN.1 notation this is any object preceded by
 * a [n] where n is some number - these are assumed to follow the construction
 * rules (as with sequences).
 */
public abstract class ASN1TaggedObject
    extends ASN1Primitive
    implements ASN1TaggedObjectParser
{
    final boolean       explicit;
    final int           tagClass;
    final int           tagNo;
    final ASN1Encodable obj;

    static public ASN1TaggedObject getInstance(
        ASN1TaggedObject    obj,
        boolean             explicit)
    {
        if (explicit)
        {
            return getInstance(obj.getObject());
        }

        throw new IllegalArgumentException("implicitly tagged tagged object");
    }

    static public ASN1TaggedObject getInstance(
        Object obj) 
    {
        if (obj == null || obj instanceof ASN1TaggedObject) 
        {
            return (ASN1TaggedObject)obj;
        }
        else if (obj instanceof byte[])
        {
            try
            {
                return ASN1TaggedObject.getInstance(fromByteArray((byte[])obj));
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("failed to construct tagged object from byte[]: " + e.getMessage());
            }
        }

        throw new IllegalArgumentException("unknown object in getInstance: " + obj.getClass().getName());
    }

    /**
     * Create a tagged object with the style given by the value of explicit.
     * <p>
     * If the object implements ASN1Choice the tag style will always be changed
     * to explicit in accordance with the ASN.1 encoding rules.
     * </p>
     * @param explicit true if the object is explicitly tagged.
     * @param tagNo the tag number for this object.
     * @param obj the tagged object.
     */
    protected ASN1TaggedObject(boolean explicit, int tagNo, ASN1Encodable obj)
    {
        this(explicit, BERTags.CONTEXT_SPECIFIC, tagNo, obj);
    }

    protected ASN1TaggedObject(boolean explicit, int tagClass, int tagNo, ASN1Encodable obj)
    {
        if (null == obj)
        {
            throw new NullPointerException("'obj' cannot be null");
        }
        if (tagClass == BERTags.UNIVERSAL || (tagClass & BERTags.PRIVATE) != tagClass)
        {
            throw new IllegalArgumentException("invalid tag class: " + tagClass);
        }

        this.explicit = explicit || (obj instanceof ASN1Choice);
        this.tagClass = tagClass;
        this.tagNo = tagNo;
        this.obj = obj;
    }

    boolean asn1Equals(ASN1Primitive other)
    {
        if (!(other instanceof ASN1TaggedObject))
        {
            return false;
        }

        ASN1TaggedObject that = (ASN1TaggedObject)other;

        if (this.tagNo != that.tagNo ||
            this.tagClass != that.tagClass ||
            this.explicit != that.explicit)
        {
            return false;
        }

        ASN1Primitive p1 = this.obj.toASN1Primitive();
        ASN1Primitive p2 = that.obj.toASN1Primitive();

        return p1 == p2 || p1.asn1Equals(p2);
    }

    public int hashCode()
    {
        return (tagClass * 7919) ^ tagNo ^ (explicit ? 0x0F : 0xF0) ^ obj.toASN1Primitive().hashCode();
    }

    public int getTagClass()
    {
        return tagClass;
    }

    /**
     * Return the tag number associated with this object.
     *
     * @return the tag number.
     */
    public int getTagNo()
    {
        return tagNo;
    }

    /**
     * return whether or not the object may be explicitly tagged. 
     * <p>
     * Note: if the object has been read from an input stream, the only
     * time you can be sure if isExplicit is returning the true state of
     * affairs is if it returns false. An implicitly tagged object may appear
     * to be explicitly tagged, so you need to understand the context under
     * which the reading was done as well, see getObject below.
     */
    public boolean isExplicit()
    {
        return explicit;
    }

    /**
     * Return whatever was following the tag.
     * <p>
     * Note: tagged objects are generally context dependent if you're
     * trying to extract a tagged object you should be going via the
     * appropriate getInstance method.
     */
    public ASN1Primitive getObject()
    {
        return obj.toASN1Primitive();
    }

    /**
     * Return the object held in this tagged object as a parser assuming it has
     * the type of the passed in tag. If the object doesn't have a parser
     * associated with it, the base object is returned.
     */
    public ASN1Encodable getObjectParser(
        int     tag,
        boolean isExplicit)
        throws IOException
    {
        switch (tag)
        {
        case BERTags.SET:
            return ASN1Set.getInstance(this, isExplicit).parser();
        case BERTags.SEQUENCE:
            return ASN1Sequence.getInstance(this, isExplicit).parser();
        case BERTags.OCTET_STRING:
            return ASN1OctetString.getInstance(this, isExplicit).parser();
        }

        if (isExplicit)
        {
            return getObject();
        }

        throw new ASN1Exception("implicit tagging not implemented for tag: " + tag);
    }

    public ASN1Primitive getLoadedObject()
    {
        return this.toASN1Primitive();
    }

    ASN1Primitive toDERObject()
    {
        return new DERTaggedObject(explicit, tagClass, tagNo, obj);
    }

    ASN1Primitive toDLObject()
    {
        return new DLTaggedObject(explicit, tagClass, tagNo, obj);
    }

    public String toString()
    {
        return "[" + getTagText(tagClass, tagNo) + "]" + obj;
    }

    private static String getTagText(int tagClass, int tagNo)
    {
        switch (tagClass)
        {
        case BERTags.APPLICATION:
            return "APPLICATION " + tagNo;
        case BERTags.CONTEXT_SPECIFIC:
            return "CONTEXT " + tagNo;
        case BERTags.PRIVATE:
            return "PRIVATE " + tagNo;
        default:
            return Integer.toString(tagNo);
        }
    }

    static ASN1Primitive createConstructed(int tagClass, int tagNo, boolean isIL,
        ASN1EncodableVector contentsElements)
    {
        boolean maybeExplicit = (contentsElements.size() == 1);

        if (isIL)
        {
            switch (tagClass)
            {
            case BERTags.APPLICATION:
                return new BERApplicationSpecific(tagNo, contentsElements);
            default:
            {
                return maybeExplicit
                    ?   new BERTaggedObject(true, tagClass, tagNo, contentsElements.get(0))
                    :   new BERTaggedObject(false, tagClass, tagNo, BERFactory.createSequence(contentsElements));
            }
            }
        }

        switch (tagClass)
        {
        case BERTags.APPLICATION:
            return new DLApplicationSpecific(tagNo, contentsElements);
        default:
        {
            return maybeExplicit
                ?   new DLTaggedObject(true, tagClass, tagNo, contentsElements.get(0))
                :   new DLTaggedObject(false, tagClass, tagNo, DLFactory.createSequence(contentsElements));
        }
        }
    }

    static ASN1Primitive createPrimitive(int tagClass, int tagNo, byte[] contentsOctets)
    {
        switch (tagClass)
        {
        case BERTags.APPLICATION:
            return new DLApplicationSpecific(false, tagNo, contentsOctets);
        default:
            // Note: !CONSTRUCTED => IMPLICIT
            return new DLTaggedObject(false, tagClass, tagNo, new DEROctetString(contentsOctets));
        }
    }
}
