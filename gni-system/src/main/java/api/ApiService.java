package api;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;

import java.util.Map;

/**
 * @author Noel
 * @version 1
 */
@RequestMapping("/api")
public final class ApiService {

    @RequestMapping(value = "/request")
    public void handleApiRequest(final Callback<String> callback, final @RequestParam("request") String requestJson) {
        try {
            JSONRPC2Request request = JSONRPC2Request.parse(requestJson);
            String method = request.getMethod();
            Map<String, Object> params = request.getNamedParams();
            switch (method) {
                case "openAccount":             callback.reply(openAccountHandler(params).toJSONString());
                    break;
                case "openAdditionalAccount":   callback.reply(openAdditionalAccountHandler(params).toJSONString());
                    break;
                case "closeAccount":            callback.reply(closeAccountHandler(params).toJSONString());
                    break;
                case "provideAccess":           callback.reply(provideAccessHandler(params).toJSONString());
                    break;
                case "revokeAccess":            callback.reply(revokeAccessHandler(params).toJSONString());
                    break;
                case "depositIntoAccount":      callback.reply(depositIntoAccountHandler(params).toJSONString());
                    break;
                case "payFromAccount":          callback.reply(payFromAccountHandler(params).toJSONString());
                    break;
                case "transferMoney":           callback.reply(transferMoneyHandler(params).toJSONString());
                    break;
                case "getAuthToken":            callback.reply(getAuthTokenHandler(params).toJSONString());
                    break;
                case "getBalance":              callback.reply(getBalanceHandler(params).toJSONString());
                    break;
                case "getTransactionsOverview": callback.reply(getTransactionsOverviewHandler(params).toJSONString());
                    break;
                case "getUserAccess":           callback.reply(getUserAccessHandler(params).toJSONString());
                    break;
                case "getBankAccountAccess":    callback.reply(getBankAccountAccessHandler(params).toJSONString());
                    break;
                default:                        callback.reply(new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND,
                        request.getID()).toJSONString());
                    break;
            }
        } catch (JSONRPC2ParseException e) {
            callback.reply(new JSONRPC2Response(JSONRPC2Error.PARSE_ERROR).toJSONString());
        }
    }

    private JSONRPC2Response openAccountHandler(final Map<String, Object> params) {

    }
    private JSONRPC2Response openAdditionalAccountHandler(final Map<String, Object> params) {}
    private JSONRPC2Response closeAccountHandler(final Map<String, Object> params) {}
    private JSONRPC2Response provideAccessHandler(final Map<String, Object> params) {}
    private JSONRPC2Response revokeAccessHandler(final Map<String, Object> params) {}
    private JSONRPC2Response depositIntoAccountHandler(final Map<String, Object> params) {}
    private JSONRPC2Response payFromAccountHandler(final Map<String, Object> params) {}
    private JSONRPC2Response transferMoneyHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getAuthTokenHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getBalanceHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getTransactionsOverviewHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getUserAccessHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getBankAccountAccessHandler(final Map<String, Object> params) {}
}
