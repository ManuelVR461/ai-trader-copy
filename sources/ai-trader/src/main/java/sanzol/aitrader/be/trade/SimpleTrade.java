package sanzol.aitrader.be.trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import api.client.futures.impl.SyncFuturesClient;
import api.client.futures.model.Order;
import api.client.futures.model.PositionRisk;
import api.client.futures.model.enums.NewOrderRespType;
import api.client.futures.model.enums.OrderSide;
import api.client.futures.model.enums.OrderType;
import api.client.futures.model.enums.PositionSide;
import api.client.futures.model.enums.TimeInForce;
import api.client.futures.model.enums.WorkingType;
import sanzol.aitrader.be.model.GOrder;
import sanzol.aitrader.be.model.Symbol;
import sanzol.aitrader.be.service.PositionFuturesService;

public class SimpleTrade
{

	public static Map<String, GOrder> calc(Symbol coin, String posSide, BigDecimal posPrice, BigDecimal posQty, BigDecimal shootPrice, BigDecimal shootQty) throws Exception
	{
		Map<String, GOrder> mapPosition = new HashMap<String, GOrder>();

		// --- POSITION -------------------------------------------------------
		PositionRisk positionRisk = PositionFuturesService.getPositionRisk(coin.getPair());
		boolean positionExists = (positionRisk != null && positionRisk.getPositionAmt().compareTo(BigDecimal.ZERO) != 0);
		if (positionExists)
		{
			posSide = positionRisk.getPositionAmt().doubleValue() < 0 ? "SELL" : "BUY";
			posPrice = positionRisk.getEntryPrice();
			posQty = positionRisk.getPositionAmt().abs();
		}
		mapPosition.put("POS", new GOrder(posPrice, posQty));

		// --- SHOOT ----------------------------------------------------------
		mapPosition.put("SHOOT", new GOrder(shootPrice, shootQty));

		// --- RESULT ---------------------------------------------------------
		BigDecimal resultQty = posQty.add(shootQty);
		BigDecimal resultUSD = posPrice.multiply(posQty).add(shootPrice.multiply(shootQty));
		BigDecimal resultPrice = resultUSD.divide(resultQty, RoundingMode.HALF_UP);
		mapPosition.put("RESULT", new GOrder(resultPrice, resultQty, resultUSD));

		// --- DISTANCES ------------------------------------------------------
		if ("BUY".equals(posSide))
		{
			BigDecimal shootDist = BigDecimal.ONE.subtract(posPrice.divide(shootPrice, RoundingMode.HALF_UP));
			mapPosition.get("SHOOT").setDist(shootDist);
			BigDecimal resultDist = BigDecimal.ONE.subtract(resultPrice.divide(shootPrice, RoundingMode.HALF_UP));
			mapPosition.get("RESULT").setDist(resultDist);
		}
		else if ("SELL".equals(posSide))
		{
			BigDecimal shootDist = posPrice.divide(shootPrice, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
			mapPosition.get("SHOOT").setDist(shootDist);
			BigDecimal resultDist = resultPrice.divide(shootPrice, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
			mapPosition.get("RESULT").setDist(resultDist);
		}

		return mapPosition;
	}

	public static String postOrder(Symbol symbol, String side, BigDecimal price, BigDecimal coins) throws KeyManagementException, InvalidKeyException, NoSuchAlgorithmException
	{
		OrderSide orderSide = "SHORT".equals(side) ? OrderSide.SELL : "LONG".equals(side) ? OrderSide.BUY : null;

		Order orderResult = postOrder(
								symbol, orderSide, OrderType.LIMIT, TimeInForce.GTC,
								symbol.qtyToStr(coins), symbol.priceToStr(price), null, null, null, WorkingType.CONTRACT_PRICE,
								NewOrderRespType.RESULT, null);

		return (orderResult != null ? orderResult.getStatus() : "n/a");
	}

	public static String postTprofit(Symbol symbol, String side, BigDecimal price, BigDecimal coins) throws KeyManagementException, InvalidKeyException, NoSuchAlgorithmException
	{
		OrderSide orderSide = "SHORT".equals(side) ? OrderSide.BUY : "LONG".equals(side) ? OrderSide.SELL : null;

		Order orderResult = postOrder(
								symbol, orderSide, OrderType.LIMIT, TimeInForce.GTC,
								symbol.qtyToStr(coins), symbol.priceToStr(price), true, null, null, WorkingType.CONTRACT_PRICE,
								NewOrderRespType.RESULT, null);

		return (orderResult != null ? orderResult.getStatus() : "n/a");
	}

	public static String postSMarket(Symbol symbol, String side, BigDecimal price) throws KeyManagementException, InvalidKeyException, NoSuchAlgorithmException
	{
		OrderSide orderSide = "SHORT".equals(side) ? OrderSide.BUY : "LONG".equals(side) ? OrderSide.SELL : null;

		Order orderResult = postOrder(
								symbol, orderSide, OrderType.STOP_MARKET, TimeInForce.GTE_GTC,
								null, null, null, null, symbol.priceToStr(price), WorkingType.CONTRACT_PRICE,
								NewOrderRespType.RESULT, true);

		return (orderResult != null ? orderResult.getStatus() : "n/a");
	}

	public static void cancelOrder(Order order) throws KeyManagementException, InvalidKeyException, NoSuchAlgorithmException
	{
		SyncFuturesClient.cancelOrder(order.getSymbol(), order.getOrderId(), null);
	}

	private static Order postOrder(Symbol symbol, OrderSide side, OrderType orderType, TimeInForce timeInForce,
								   String quantity, String price, Boolean reduceOnly, String newClientOrderId,
								   String stopPrice, WorkingType workingType, NewOrderRespType newOrderRespType, Boolean closePosition) throws KeyManagementException, InvalidKeyException, NoSuchAlgorithmException
	{
		return SyncFuturesClient.postOrder(symbol.getPair(), side, PositionSide.BOTH, orderType, timeInForce,
								   		   quantity, price, reduceOnly, newClientOrderId, stopPrice, workingType, newOrderRespType, closePosition);
	}

}
