package com.facebook.presto;

import com.facebook.presto.slice.Slice;
import com.facebook.presto.slice.Slices;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndex;

public class DictionaryEncodedCursor implements Cursor
{
    private final TupleInfo tupleInfo;
    private final Slice[] dictionary;
    private final Map<Slice, Tuple> reverseDictionary;
    private final Cursor sourceCursor;

    public DictionaryEncodedCursor(TupleInfo tupleInfo, Slice[] dictionary, Cursor sourceCursor)
    {
        checkNotNull(tupleInfo, "tupleInfo is null");
        checkNotNull(dictionary, "dictionary is null");
        checkNotNull(sourceCursor, "sourceCursor is null");
        checkArgument(tupleInfo.getFieldCount() == 1, "tupleInfo should only have one column");

        this.tupleInfo = tupleInfo;
        this.dictionary = dictionary;
        this.sourceCursor = sourceCursor;

        ImmutableMap.Builder<Slice, Tuple> builder = ImmutableMap.builder();
        for (int i = 0; i < dictionary.length; i++) {
            builder.put(
                    dictionary[i],
                    new Tuple(
                            Slices.allocate(SizeOf.SIZE_OF_LONG)
                                    .output()
                                    .appendLong(i)
                                    .slice(),
                            TupleInfo.SINGLE_LONG_TUPLEINFO
                    )

            );
        }
        reverseDictionary = builder.build();
    }

    @Override
    public TupleInfo getTupleInfo()
    {
        return tupleInfo;
    }

    @Override
    public boolean advanceNextValue()
    {
        return sourceCursor.advanceNextValue();
    }

    @Override
    public boolean advanceNextPosition()
    {
        return sourceCursor.advanceNextPosition();
    }

    @Override
    public Tuple getTuple()
    {
        return new Tuple(getSlice(0), tupleInfo);
    }

    @Override
    public long getLong(int field)
    {
        checkArgument(field == 0, "should only have one field");
        return tupleInfo.getLong(getSlice(0), 0);
    }

    @Override
    public Slice getSlice(int field)
    {
        checkArgument(field == 0, "should only have one field");
        // This should be very memory efficient since the returned cursor is just the cached dictionary value
        return decodeSliceValue(Ints.checkedCast(sourceCursor.getLong(0)));
    }

    @Override
    public long getPosition()
    {
        return sourceCursor.getPosition();
    }

    @Override
    public boolean currentValueEquals(Tuple value)
    {
        checkNotNull(value, "value is null");
        return value.size() == 1 && getSlice(0).equals(value.getTupleSlice());
    }

    @Override
    public boolean advanceToPosition(long position)
    {
        return sourceCursor.advanceToPosition(position);
    }

    @Override
    public long getCurrentValueEndPosition()
    {
        return sourceCursor.getCurrentValueEndPosition();
    }

    private Slice decodeSliceValue(int dictionaryKey) {
        checkPositionIndex(dictionaryKey, dictionary.length, "Invalid dictionary key");
        return dictionary[dictionaryKey];
    }
}
