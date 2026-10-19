package com.frontendbase.api.returns.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import com.frontendbase.api.stock.dto.StockDtos.LineResponse;
import static org.junit.jupiter.api.Assertions.*;
class ReturnValuationTest {
    @Test void largestRemainderHasStableUuidTieAndExactFullReturn() {
        var a=UUID.fromString("00000000-0000-0000-0000-000000000001");
        var b=UUID.fromString("80000000-0000-0000-0000-000000000001");
        var lines=List.of(new LineResponse(b,3,1,3,"B","B","cay"),new LineResponse(a,3,1,3,"A","A","cay"));
        var discounts=ReturnValuation.discounts(lines,6,1);
        assertEquals(1,discounts.get(a));assertEquals(0,discounts.get(b));
        for(long net:List.of(0L,2L,Long.MAX_VALUE)) {
            long sum=0;
            for(int i=0;i<3;i++) sum=Math.addExact(sum,ReturnValuation.amount(net,3,i,1));
            assertEquals(net,sum);
        }
    }
    @Test void intermediateMultiplicationExceedsLongWithoutLosingPrecision() {
        var id=UUID.randomUUID(); long max=Long.MAX_VALUE;
        assertEquals(max-1,ReturnValuation.discounts(List.of(new LineResponse(id,1,max,max,"P","P","cay")),max,max-1).get(id));
        assertEquals(max,ReturnValuation.amount(max,max,0,max));
        assertEquals(0,ReturnValuation.discounts(List.of(new LineResponse(id,1,0,0,"P","P","cay")),0,0).get(id));
    }
}
