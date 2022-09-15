package com.bob.walletdemo.wallet;

import static org.web3j.crypto.Hash.sha256;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;


import com.bob.walletdemo.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.crypto.Bip39Wallet;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletFile;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.ObjectMapperFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("unused")
public class ETHWalletHelper {

    private static final String TAG = ETHWalletHelper.class.getSimpleName();

    public static final String GANACHE_SERVER_URL = "http://192.168.132.67:8545";
    public static final String MNEMONIC = "wood table canoe submit fold page dress auto tell biology appear recipe";//my ganache account
    public static final String PASSWORD = "111111";

    private static final String CONTRACT_ADDRESS = "0x7ACa5Da559a60ba12c1379ad5eADA2C39e7C6644";
    private static final String GAS_LIMIT = "3000000";

    public static File PATH = new File(Environment.getExternalStorageDirectory().getPath());


    public static volatile ETHWalletHelper instance;

    public static ETHWalletHelper getInstance() {
        if (instance == null) {
            synchronized (ETHWalletHelper.class) {
                if (instance == null) {
                    instance = new ETHWalletHelper();
                }
            }
        }
        return instance;
    }

    private final ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private Bip39Wallet mWallet = null;

    //**************创建钱包*****************

    public void generateBip39Wallets() {

        try {
            mWallet = WalletUtils.generateBip39Wallet(ETHWalletHelper.PASSWORD, ETHWalletHelper.PATH);
        } catch (CipherException | IOException e) {
            e.printStackTrace();
        }

        if (mWallet == null) return;

        String filename = mWallet.getFilename();
        File file = new File(ETHWalletHelper.PATH + File.separator + filename);

        WalletFile walletFile = null;
        try {
            walletFile = objectMapper.readValue(file, WalletFile.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        assert walletFile != null;
        Log.d(TAG, "generateBip39Wallets: ====address:" + walletFile.getAddress()
                + " getMnemonic:" + mWallet.getMnemonic()
                + " wallet:" + mWallet.toString()
                + " walletFile:" + walletFile);
    }

    public String getMnemonic() {
        if (mWallet == null) return "";

        String mnemonic = mWallet.getMnemonic();
        Log.d(TAG, "getMnemonic: ====" + mnemonic);
        return mnemonic;
    }

    public String getPrivateKey() {
        if (mWallet == null) return "";

        byte[] seed = MnemonicUtils.generateSeed(mWallet.getMnemonic(), ETHWalletHelper.PASSWORD);
        Credentials credentials = Credentials.create(ECKeyPair.create(sha256(seed)));
        String privateKey = credentials.getEcKeyPair().getPrivateKey().toString();
        String publicKey = credentials.getEcKeyPair().getPublicKey().toString();
        Log.d(TAG, "getPrivateKey: ====:privateKey:" + privateKey + " publicKey:" + publicKey);
        return privateKey;
    }

    /**
     * json导入钱包
     */
    public static String loadJsonCredentials(String filePath) {
        Credentials credentials = null;
        try {
            String json = FileUtil.readJsonFile(filePath);
            credentials = WalletUtils.loadJsonCredentials(PASSWORD, json);
        } catch (IOException | CipherException e) {
            e.printStackTrace();
        }

        ECKeyPair ecKeyPair = null;
        if (credentials != null) {
            ecKeyPair = credentials.getEcKeyPair();
        }

        BigInteger privateKey = null;
        BigInteger publicKey = null;
        if (ecKeyPair != null) {
            privateKey = ecKeyPair.getPrivateKey();
            publicKey = ecKeyPair.getPublicKey();

        }

        assert credentials != null;

        String address = credentials.getAddress();
        Log.d(TAG, "importWalletByKeyStore: ====address:" + address
                + " privateKey:" + privateKey
                + " publicKey:" + publicKey);
        return address;
    }



    //**************连接钱包*****************

    private Web3j web3j;
    private Credentials mCredentials;

    public String connectWallet(String serverUrl, String password, String mnemonic) {
        web3j = Web3j.build(new HttpService(serverUrl));

        Web3ClientVersion web3ClientVersion = null;
        try {
            web3ClientVersion = web3j.web3ClientVersion().sendAsync().get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        if (web3ClientVersion != null) {
            String clientVersion = web3ClientVersion.getWeb3ClientVersion();
            Log.d(TAG, "connectWallet success: ====clientVersion:" + clientVersion);
        }

        mCredentials = WalletUtils.loadBip39Credentials(password, mnemonic);

        String address = mCredentials.getAddress();
        Log.d(TAG, "connectWallet: ====address:" + address);
        return address;
    }

    /**
     * todo throw Exception
     * 获得某个账户余额，大整数类型
     */
    public String getAccountBalance(String address) {
        if (web3j == null) return "";
        if (TextUtils.isEmpty(address)) return "";

        //获取指定钱包的以太币余额
        EthGetBalance ethGetBlance = null;
        try {
            ethGetBlance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 格式转换 WEI(币种单位) --> ETHER
        String balance = "";
        if (ethGetBlance != null) {
            balance = Convert.fromWei(new BigDecimal(ethGetBlance.getBalance()), Convert.Unit.ETHER).toPlainString();
        }

        return balance;

    }


    /**
     * todo
     * 调用合约
     * 需要支付gas的方法
     */
    public void setValue(String userAddress, String method, String value) {
        Function function = new Function(
                method,
                Collections.singletonList(new Utf8String(value)),
                Collections.emptyList());
        BigInteger nonce = getNonce(userAddress);
        String encodedFunction = FunctionEncoder.encode(function);

        BigInteger gasLimit = new BigInteger(GAS_LIMIT);
        RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, DefaultGasProvider.GAS_PRICE, gasLimit, CONTRACT_ADDRESS, encodedFunction);

        EthSendTransaction response = null;
        try {
            response = web3j.ethSendRawTransaction(Numeric.toHexString(TransactionEncoder.signMessage(rawTransaction, mCredentials)))
                    .sendAsync()
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        String transactionHash = null;
        if (response != null) {
            transactionHash = response.getTransactionHash();
        }
        Log.d(TAG, "store: ====transactionHash:" + transactionHash);
    }

    /**
     * 调用合约的只读方法，无需gas
     */
    public String getValue(String method) {

        Function function = new Function(
                method,
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Utf8String>() {
                }));

        String encodedFunction = FunctionEncoder.encode(function);
        EthCall response = null;
        try {
            response = web3j.ethCall(
                            Transaction.createEthCallTransaction(null, CONTRACT_ADDRESS, encodedFunction),
                            DefaultBlockParameterName.LATEST)
                    .sendAsync().get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        String result = "";
        if (response != null) {
            result = response.getResult();
        }
        Log.d(TAG, "callContract: ====result:" + result);

        return result;

    }


    /**
     * todo request.sendAsync().get() IOException
     */
    private BigInteger getNonce(String userAddress) {
        BigInteger nonce = BigInteger.ONE;
        try {
            Request<?, EthGetTransactionCount> request = web3j.ethGetTransactionCount(
                    userAddress, DefaultBlockParameterName.PENDING);
            EthGetTransactionCount ethGetTransactionCount = request.sendAsync().get();
            nonce = ethGetTransactionCount.getTransactionCount();
        } catch (Exception e) {
            System.out.println("" + e);
        }
        return nonce;
    }




}
