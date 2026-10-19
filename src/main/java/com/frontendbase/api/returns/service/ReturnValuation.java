package com.frontendbase.api.returns.service;
import java.math.BigInteger;
import java.util.*;
import com.frontendbase.api.sales.dto.SalesDtos.SalesResponse;
import com.frontendbase.api.stock.dto.StockDtos.LineResponse;

/** Reproducible from immutable confirmed order totals and lines. */
public final class ReturnValuation {
    private ReturnValuation() {}
    public static Map<UUID,Long> discounts(SalesResponse order) {
        return discounts(order.lines(), order.subtotal(), order.discountAmount());
    }
    public static Map<UUID,Long> discounts(List<LineResponse> lines, long subtotal, long discount) {
        Map<UUID,Long> result = new HashMap<>();
        Map<UUID,BigInteger> remainders = new HashMap<>();
        long allocated=0;
        for (var l:lines) {
            var qr=subtotal==0 ? new BigInteger[]{BigInteger.ZERO,BigInteger.ZERO}
                : BigInteger.valueOf(l.lineTotal()).multiply(BigInteger.valueOf(discount))
                    .divideAndRemainder(BigInteger.valueOf(subtotal));
            result.put(l.productId(),qr[0].longValueExact());
            remainders.put(l.productId(),qr[1]);
            allocated=Math.addExact(allocated,qr[0].longValueExact());
        }
        var ranked=lines.stream().sorted(Comparator.<LineResponse,BigInteger>comparing(l->remainders.get(l.productId()))
            .reversed().thenComparing(l->l.productId().toString())).toList();
        for(int i=0;i<discount-allocated;i++) {
            UUID id=ranked.get(i).productId(); result.put(id,result.get(id)+1);
        }
        return result;
    }
    public static long amount(long net, long sold, long before, long quantity) {
        BigInteger n=BigInteger.valueOf(net), d=BigInteger.valueOf(sold);
        return n.multiply(BigInteger.valueOf(Math.addExact(before,quantity))).divide(d)
            .subtract(n.multiply(BigInteger.valueOf(before)).divide(d)).longValueExact();
    }
}
