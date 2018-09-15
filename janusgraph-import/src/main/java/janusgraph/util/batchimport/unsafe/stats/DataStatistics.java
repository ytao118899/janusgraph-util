package janusgraph.util.batchimport.unsafe.stats;


import janusgraph.util.batchimport.unsafe.helps.collection.Iterators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static java.lang.Integer.max;
import static java.lang.String.format;

/**
 * Keeps data about how edges are distributed between different types.
 */
public class DataStatistics implements Iterable<DataStatistics.EdgeTypeCount>
{
    private final List<Client> clients = new ArrayList<>();
    private int opened;
    private EdgeTypeCount[] typeCounts;
    private final long nodeCount;
    private final long propertyCount;

    public DataStatistics(long nodeCount, long propertyCount, EdgeTypeCount[] sortedTypes )
    {
        this.nodeCount = nodeCount;
        this.propertyCount = propertyCount;
        this.typeCounts = sortedTypes;
    }

    @Override
    public Iterator<EdgeTypeCount> iterator()
    {
        return Iterators.iterator( typeCounts );
    }

    public int getNumberOfEdgeTypes()
    {
        return typeCounts.length;
    }

    public synchronized Client newClient()
    {
        Client client = new Client();
        clients.add( client );
        opened++;
        return client;
    }

    private synchronized void closeClient()
    {
        if ( --opened == 0 )
        {
            int highestTypeId = 0;
            for ( Client client : clients )
            {
                highestTypeId = max( highestTypeId, client.highestTypeId );
            }

            long[] counts = new long[highestTypeId + 1];
            for ( Client client : clients )
            {
                client.addTo( counts );
            }
            typeCounts = new EdgeTypeCount[counts.length];
            for ( int i = 0; i < counts.length; i++ )
            {
                typeCounts[i] = new EdgeTypeCount( i, counts[i] );
            }
            Arrays.sort( typeCounts );
        }
    }

    public static class EdgeTypeCount implements Comparable<EdgeTypeCount>
    {
        private final int typeId;
        private final long count;

        public EdgeTypeCount(int typeId, long count )
        {
            this.typeId = typeId;
            this.count = count;
        }

        public int getTypeId()
        {
            return typeId;
        }

        public long getCount()
        {
            return count;
        }

        @Override
        public int compareTo( EdgeTypeCount o )
        {
            return Long.compare( count, o.count );
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (count ^ (count >>> 32));
            result = prime * result + typeId;
            return result;
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            if ( obj == null || getClass() != obj.getClass() )
            {
                return false;
            }
            EdgeTypeCount other = (EdgeTypeCount) obj;
            return count == other.count && typeId == other.typeId;
        }

        @Override
        public String toString()
        {
            return format( "%s[type:%d, count:%d]", getClass().getSimpleName(), typeId, count );
        }
    }

    public class Client implements AutoCloseable
    {
        private long[] counts = new long[8]; // index is edgeLabel id
        private int highestTypeId;

        public void increment( int typeId )
        {
            if ( typeId >= counts.length )
            {
                counts = Arrays.copyOf( counts, max( counts.length * 2, typeId + 1 ) );
            }
            counts[typeId]++;
            if ( typeId > highestTypeId )
            {
                highestTypeId = typeId;
            }
        }

        @Override
        public void close()
        {
            closeClient();
        }

        private void addTo( long[] counts )
        {
            for ( int i = 0; i <= highestTypeId; i++ )
            {
                counts[i] += this.counts[i];
            }
        }
    }

    public EdgeTypeCount get(int index )
    {
        return typeCounts[index];
    }

    /*public PrimitiveIntSet types(int startingFromType, int upToType )
    {
        PrimitiveIntSet set = Primitive.intSet( (upToType - startingFromType) * 2 );
        for ( int i = startingFromType; i < upToType; i++ )
        {
            set.add( get( i ).getTypeId() );
        }
        return set;
    }*/

    public long getNodeCount()
    {
        return nodeCount;
    }

    public long getPropertyCount()
    {
        return propertyCount;
    }

    public long getEdgeCount()
    {
        long sum = 0;
        for ( EdgeTypeCount type : typeCounts )
        {
            sum += type.count;
        }
        return sum;
    }

    @Override
    public String toString()
    {
        return format( "Imported:%n  %d nodes%n  %d edges%n  %d properties", nodeCount, getEdgeCount(), propertyCount );
    }
}
