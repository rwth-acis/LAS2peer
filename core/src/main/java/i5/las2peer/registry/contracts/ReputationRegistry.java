package i5.las2peer.registry.contracts;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple6;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>
 * Auto generated code.
 * <p>
 * <strong>Do not modify!</strong>
 * <p>
 * Please use the <a href="https://docs.web3j.io/command_line.html">web3j
 * command line tools</a>, or the
 * org.web3j.codegen.SolidityFunctionWrapperGenerator in the
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen
 * module</a> to update.
 *
 * <p>
 * Generated with web3j version 4.3.0.
 */
public class ReputationRegistry extends Contract {
    private static final String BINARY = "6080604052600560009081556001819055600a60025560035534801561002457600080fd5b506040516111473803806111478339818101604052602081101561004757600080fd5b50506110ef806100586000396000f3fe608060405234801561001057600080fd5b50600436106100b45760003560e01c80637740f92f116100715780637740f92f1461037a5780637a2bba611461041e578063a787c80b14610457578063bbe1562714610491578063d4800b40146104b7578063d9cf8655146104d4576100b4565b806306220d88146100b95780631eefe9b9146100f157806344a9f6761461011f5780635e4177f2146102e957806367c9b51a1461030f5780636dae0a5614610317575b600080fd5b6100df600480360360208110156100cf57600080fd5b50356001600160a01b03166104fa565b60408051918252519081900360200190f35b61011d6004803603604081101561010757600080fd5b506001600160a01b03813516906020013561056d565b005b61011d600480360360c081101561013557600080fd5b6001600160a01b038235169160208101359160408201359190810190608081016060820135600160201b81111561016b57600080fd5b82018360208201111561017d57600080fd5b803590602001918460018302840111600160201b8311171561019e57600080fd5b91908080601f0160208091040260200160405190810160405280939291908181526020018383808284376000920191909152509295949360208101935035915050600160201b8111156101f057600080fd5b82018360208201111561020257600080fd5b803590602001918460018302840111600160201b8311171561022357600080fd5b91908080601f0160208091040260200160405190810160405280939291908181526020018383808284376000920191909152509295949360208101935035915050600160201b81111561027557600080fd5b82018360208201111561028757600080fd5b803590602001918460018302840111600160201b831117156102a857600080fd5b91908080601f0160208091040260200160405190810160405280939291908181526020018383808284376000920191909152509295506106f6945050505050565b6100df600480360360208110156102ff57600080fd5b50356001600160a01b031661070d565b6100df61077c565b61033d6004803603602081101561032d57600080fd5b50356001600160a01b0316610782565b604080516001600160a01b0390971687526020870195909552858501939093526060850191909152608084015260a0830152519081900360c00190f35b61011d6004803603602081101561039057600080fd5b810190602081018135600160201b8111156103aa57600080fd5b8201836020820111156103bc57600080fd5b803590602001918460018302840111600160201b831117156103dd57600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600092019190915250929550610824945050505050565b61043b6004803603602081101561043457600080fd5b5035610942565b604080516001600160a01b039092168252519081900360200190f35b61047d6004803603602081101561046d57600080fd5b50356001600160a01b031661096c565b604080519115158252519081900360200190f35b61033d600480360360208110156104a757600080fd5b50356001600160a01b03166109c6565b61011d600480360360208110156104cd57600080fd5b5035610a05565b6100df600480360360208110156104ea57600080fd5b50356001600160a01b0316610a18565b60006105058261096c565b61054a576040805162461bcd60e51b81526020600482015260116024820152701c1c9bd99a5b19481b9bdd08199bdd5b99607a1b604482015290519081900360640190fd5b506001600160a01b0381166000908152600560205260409020600301545b919050565b6105763361096c565b6105b1576105b16040518060400160405280601681526020017539b2b73232b910383937b334b632903ab735b737bbb760511b815250610824565b6105ba8261096c565b6105fc576105fc6040518060400160405280601a81526020017f636f6e74726168656e742070726f66696c6520756e6b6e6f776e000000000000815250610824565b6000548113801561060e575060015481125b156106345761063460405180606001604052806039815260200161108260399139610824565b336001600160a01b038316141561067a5761067a6040518060400160405280601481526020017321b0b73737ba103930ba32903cb7bab939b2b63360611b815250610824565b600080610688338585610a87565b91509150610697338583610af3565b506106a133610bed565b506106ab84610c92565b506001600160a01b038416600081815260056020908152604080832060020185905533808452818420948452600690940190915290208390556106f090858584610d37565b50505050565b61070533878787878787610d90565b505050505050565b60006107188261096c565b61075d576040805162461bcd60e51b81526020600482015260116024820152701c1c9bd99a5b19481b9bdd08199bdd5b99607a1b604482015290519081900360640190fd5b506001600160a01b031660009081526005602052604090206002015490565b60045490565b6000806000806000806107948761096c565b6107d9576040805162461bcd60e51b81526020600482015260116024820152701c1c9bd99a5b19481b9bdd08199bdd5b99607a1b604482015290519081900360640190fd5b505050506001600160a01b0392831660009081526005602081905260409091208054600182015460028301546003840154600485015494909501549290971697909695509293509091565b7f6222dc10afcca3c8cec10b1b08a9bff096c30eb574a3233be051aa5440fc41bb816040518080602001828103825283818151815260200191508051906020019080838360005b8381101561088357818101518382015260200161086b565b50505050905090810190601f1680156108b05780820380516001836020036101000a031916815260200191505b509250505060405180910390a18060405162461bcd60e51b81526004018080602001828103825283818151815260200191508051906020019080838360005b838110156109075781810151838201526020016108ef565b50505050905090810190601f1680156109345780820380516001836020036101000a031916815260200191505b509250505060405180910390fd5b60006004828154811061095157fe5b6000918252602090912001546001600160a01b031692915050565b60045460009061097e57506000610568565b6001600160a01b038216600081815260056020819052604090912001546004805490919081106109aa57fe5b6000918252602090912001546001600160a01b03161492915050565b60056020819052600091825260409091208054600182015460028301546003840154600485015494909501546001600160a01b03909316949193909286565b610a1433826000806000610f1d565b5050565b6000610a238261096c565b610a68576040805162461bcd60e51b81526020600482015260116024820152701c1c9bd99a5b19481b9bdd08199bdd5b99607a1b604482015290519081900360640190fd5b506001600160a01b031660009081526005602052604090206004015490565b6001600160a01b038083166000818152600560209081526040808320600290810154958916845281842094845260069094019091528120549154909283929085019190850190811315610ad957506002545b600354811215610ae857506003545b969095509350505050565b6000610afe8361096c565b610b3657610b36604051806040016040528060138152602001721c9958da5c1a595b9d081b9bdd08199bdd5b99606a1b815250610824565b610b3f8461096c565b610b7457610b746040518060400160405280601081526020016f1cd95b99195c881b9bdd08199bdd5b9960821b815250610824565b6001600160a01b03808416600081815260056020908152604080832060020187905593881680835284832084845260060182529184902086905583518681529351929391927f74c9e96f7615ed2accc025c19a2fc4eac63e018762c879dd73307716932285ed9281900390910190a35060019392505050565b6000610bf88261096c565b610c2e57610c2e604051806040016040528060118152602001701c1c9bd99a5b19481b9bdd08199bdd5b99607a1b815250610824565b6001600160a01b038216600081815260056020908152604091829020600301805460010190819055825181815292519093927fec0fb6207e4b2f6bf5fde406d30ca3449f7f717795efa8efc08aef272ef6265d92908290030190a250600192915050565b6000610c9d8261096c565b610cd357610cd3604051806040016040528060118152602001701c1c9bd99a5b19481b9bdd08199bdd5b99607a1b815250610824565b6001600160a01b038216600081815260056020908152604091829020600401805460010190819055825181815292519093927fec0fb6207e4b2f6bf5fde406d30ca3449f7f717795efa8efc08aef272ef6265d92908290030190a250600192915050565b826001600160a01b0316846001600160a01b03167f9f082b72d789110e5f5e68dfc8698d117d09c3c8f37b99de480152963bca29108484604051808381526020018281526020019250505060405180910390a350505050565b83866001600160a01b0316886001600160a01b03167f4eea43eaa97f3f895b534e3eec9db8e2a6adbc482962d585cface499479e815a8486888b60405180806020018060200180602001858152602001848103845288818151815260200191508051906020019080838360005b83811015610e15578181015183820152602001610dfd565b50505050905090810190601f168015610e425780820380516001836020036101000a031916815260200191505b50848103835287518152875160209182019189019080838360005b83811015610e75578181015183820152602001610e5d565b50505050905090810190601f168015610ea25780820380516001836020036101000a031916815260200191505b50848103825286518152865160209182019188019080838360005b83811015610ed5578181015183820152602001610ebd565b50505050905090810190601f168015610f025780820380516001836020036101000a031916815260200191505b5097505050505050505060405180910390a450505050505050565b6000610f288661096c565b15610f6457610f646040518060400160405280601681526020017570726f66696c6520616c72656164792065786973747360501b815250610824565b6040805160c0810182526001600160a01b0380891680835260208084018a81528486018a8152606086018a8152608087018a815260048054600181810183557f8a35acfbc15ff81a39ae7d344fd709f28e8600b4aa8c65c6b64bfe7fe36bd19b820180546001600160a01b03199081168b1790915560a08c019283526000998a526005988990529b90982099518a54991698909a1697909717885592519487019490945551600286015591516003850155905191830191909155915191015561102d858761103e565b506004546000190195945050505050565b6040805183815290516001600160a01b038316917f97ac5d16ba6a936f2c55834f0a22fa19a40cb24e9629f714bc2c63890139cb2f919081900360200190a2505056fe526174696e67206d75737420626520616e20696e74206265747765656e205f5f616d6f756e744d696e20616e64205f5f616d6f756e744d6178a265627a7a72315820ff92e01990ec12bc42e68fe814e091954210d97b7fbb07f81f75bbbc9ee75ee064736f6c634300050c0032";

    public static final String FUNC__GETPROFILE = "_getProfile";

    public static final String FUNC__GETUSERATINDEX = "_getUserAtIndex";

    public static final String FUNC__GETUSERCOUNT = "_getUserCount";

    public static final String FUNC__REVERT = "_revert";

    public static final String FUNC_ADDGENERICTRANSACTION = "addGenericTransaction";

    public static final String FUNC_ADDTRANSACTION = "addTransaction";

    public static final String FUNC_CREATEPROFILE = "createProfile";

    public static final String FUNC_GETCUMULATIVESCORE = "getCumulativeScore";

    public static final String FUNC_GETNOTRANSACTIONSRECEIVED = "getNoTransactionsReceived";

    public static final String FUNC_GETNOTRANSACTIONSSENT = "getNoTransactionsSent";

    public static final String FUNC_HASPROFILE = "hasProfile";

    public static final String FUNC_PROFILES = "profiles";

    public static final Event ERROREVENT_EVENT = new Event("ErrorEvent",
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {
            }));;

    public static final Event GENERICTRANSACTIONADDED_EVENT = new Event("GenericTransactionAdded",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {
            }, new TypeReference<Address>(true) {
            }, new TypeReference<Uint256>(true) {
            }, new TypeReference<Utf8String>() {
            }, new TypeReference<Utf8String>() {
            }, new TypeReference<Utf8String>() {
            }, new TypeReference<Uint256>() {
            }));;

    public static final Event TRANSACTIONADDED_EVENT = new Event("TransactionAdded",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {
            }, new TypeReference<Address>(true) {
            }, new TypeReference<Int256>() {
            }, new TypeReference<Int256>() {
            }));;

    public static final Event TRANSACTIONCOUNTCHANGED_EVENT = new Event("TransactionCountChanged",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {
            }, new TypeReference<Uint256>() {
            }));;

    public static final Event TRANSACTIONSCORECHANGED_EVENT = new Event("TransactionScoreChanged",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {
            }, new TypeReference<Address>(true) {
            }, new TypeReference<Int256>() {
            }));;

    public static final Event USERPROFILECREATED_EVENT = new Event("UserProfileCreated",
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {
            }, new TypeReference<Address>(true) {
            }));;

    @Deprecated
    protected ReputationRegistry(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice,
            BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected ReputationRegistry(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected ReputationRegistry(String contractAddress, Web3j web3j, TransactionManager transactionManager,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected ReputationRegistry(String contractAddress, Web3j web3j, TransactionManager transactionManager,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<ErrorEventEventResponse> getErrorEventEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(ERROREVENT_EVENT,
                transactionReceipt);
        ArrayList<ErrorEventEventResponse> responses = new ArrayList<ErrorEventEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ErrorEventEventResponse typedResponse = new ErrorEventEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.message = (String) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<ErrorEventEventResponse> errorEventEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, ErrorEventEventResponse>() {
            @Override
            public ErrorEventEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(ERROREVENT_EVENT, log);
                ErrorEventEventResponse typedResponse = new ErrorEventEventResponse();
                typedResponse.log = log;
                typedResponse.message = (String) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<ErrorEventEventResponse> errorEventEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ERROREVENT_EVENT));
        return errorEventEventFlowable(filter);
    }

    public List<GenericTransactionAddedEventResponse> getGenericTransactionAddedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(GENERICTRANSACTIONADDED_EVENT,
                transactionReceipt);
        ArrayList<GenericTransactionAddedEventResponse> responses = new ArrayList<GenericTransactionAddedEventResponse>(
                valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            GenericTransactionAddedEventResponse typedResponse = new GenericTransactionAddedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.sender = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.recipient = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getIndexedValues().get(2).getValue();
            typedResponse.transactionType = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.message = (String) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.txHash = (String) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.weiAmount = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<GenericTransactionAddedEventResponse> genericTransactionAddedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, GenericTransactionAddedEventResponse>() {
            @Override
            public GenericTransactionAddedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(GENERICTRANSACTIONADDED_EVENT,
                        log);
                GenericTransactionAddedEventResponse typedResponse = new GenericTransactionAddedEventResponse();
                typedResponse.log = log;
                typedResponse.sender = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.recipient = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.timestamp = (BigInteger) eventValues.getIndexedValues().get(2).getValue();
                typedResponse.transactionType = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.message = (String) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.txHash = (String) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse.weiAmount = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<GenericTransactionAddedEventResponse> genericTransactionAddedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(GENERICTRANSACTIONADDED_EVENT));
        return genericTransactionAddedEventFlowable(filter);
    }

    public List<TransactionAddedEventResponse> getTransactionAddedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(TRANSACTIONADDED_EVENT,
                transactionReceipt);
        ArrayList<TransactionAddedEventResponse> responses = new ArrayList<TransactionAddedEventResponse>(
                valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            TransactionAddedEventResponse typedResponse = new TransactionAddedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.sender = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.recipient = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.grade = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.recipientNewScore = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<TransactionAddedEventResponse> transactionAddedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, TransactionAddedEventResponse>() {
            @Override
            public TransactionAddedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(TRANSACTIONADDED_EVENT, log);
                TransactionAddedEventResponse typedResponse = new TransactionAddedEventResponse();
                typedResponse.log = log;
                typedResponse.sender = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.recipient = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.grade = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.recipientNewScore = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<TransactionAddedEventResponse> transactionAddedEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(TRANSACTIONADDED_EVENT));
        return transactionAddedEventFlowable(filter);
    }

    public List<TransactionCountChangedEventResponse> getTransactionCountChangedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(TRANSACTIONCOUNTCHANGED_EVENT,
                transactionReceipt);
        ArrayList<TransactionCountChangedEventResponse> responses = new ArrayList<TransactionCountChangedEventResponse>(
                valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            TransactionCountChangedEventResponse typedResponse = new TransactionCountChangedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.recipient = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.newScore = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<TransactionCountChangedEventResponse> transactionCountChangedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, TransactionCountChangedEventResponse>() {
            @Override
            public TransactionCountChangedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(TRANSACTIONCOUNTCHANGED_EVENT,
                        log);
                TransactionCountChangedEventResponse typedResponse = new TransactionCountChangedEventResponse();
                typedResponse.log = log;
                typedResponse.recipient = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.newScore = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<TransactionCountChangedEventResponse> transactionCountChangedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(TRANSACTIONCOUNTCHANGED_EVENT));
        return transactionCountChangedEventFlowable(filter);
    }

    public List<TransactionScoreChangedEventResponse> getTransactionScoreChangedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(TRANSACTIONSCORECHANGED_EVENT,
                transactionReceipt);
        ArrayList<TransactionScoreChangedEventResponse> responses = new ArrayList<TransactionScoreChangedEventResponse>(
                valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            TransactionScoreChangedEventResponse typedResponse = new TransactionScoreChangedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.sender = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.recipient = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.newScore = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<TransactionScoreChangedEventResponse> transactionScoreChangedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, TransactionScoreChangedEventResponse>() {
            @Override
            public TransactionScoreChangedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(TRANSACTIONSCORECHANGED_EVENT,
                        log);
                TransactionScoreChangedEventResponse typedResponse = new TransactionScoreChangedEventResponse();
                typedResponse.log = log;
                typedResponse.sender = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.recipient = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.newScore = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<TransactionScoreChangedEventResponse> transactionScoreChangedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(TRANSACTIONSCORECHANGED_EVENT));
        return transactionScoreChangedEventFlowable(filter);
    }

    public List<UserProfileCreatedEventResponse> getUserProfileCreatedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(USERPROFILECREATED_EVENT,
                transactionReceipt);
        ArrayList<UserProfileCreatedEventResponse> responses = new ArrayList<UserProfileCreatedEventResponse>(
                valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            UserProfileCreatedEventResponse typedResponse = new UserProfileCreatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.name = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<UserProfileCreatedEventResponse> userProfileCreatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, UserProfileCreatedEventResponse>() {
            @Override
            public UserProfileCreatedEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(USERPROFILECREATED_EVENT, log);
                UserProfileCreatedEventResponse typedResponse = new UserProfileCreatedEventResponse();
                typedResponse.log = log;
                typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.name = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<UserProfileCreatedEventResponse> userProfileCreatedEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(USERPROFILECREATED_EVENT));
        return userProfileCreatedEventFlowable(filter);
    }

    public RemoteCall<Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger>> _getProfile(
            String userAddress) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC__GETPROFILE,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(userAddress)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {
                }, new TypeReference<Bytes32>() {
                }, new TypeReference<Int256>() {
                }, new TypeReference<Uint256>() {
                }, new TypeReference<Uint256>() {
                }, new TypeReference<Uint256>() {
                }));
        return new RemoteCall<Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger>>(
                new Callable<Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger>>() {
                    @Override
                    public Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger> call()
                            throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger>(
                                (String) results.get(0).getValue(), (byte[]) results.get(1).getValue(),
                                (BigInteger) results.get(2).getValue(), (BigInteger) results.get(3).getValue(),
                                (BigInteger) results.get(4).getValue(), (BigInteger) results.get(5).getValue());
                    }
                });
    }

    public RemoteCall<String> _getUserAtIndex(BigInteger index) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC__GETUSERATINDEX,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(index)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {
                }));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteCall<BigInteger> _getUserCount() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC__GETUSERCOUNT,
                Arrays.<Type>asList(), Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {
                }));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteCall<TransactionReceipt> _revert(String message) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC__REVERT,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(message)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> addGenericTransaction(String contrahent, BigInteger weiAmount,
            BigInteger timestamp, String txHash, String message, String transactionType) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_ADDGENERICTRANSACTION,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(contrahent),
                        new org.web3j.abi.datatypes.generated.Uint256(weiAmount),
                        new org.web3j.abi.datatypes.generated.Uint256(timestamp),
                        new org.web3j.abi.datatypes.Utf8String(txHash), new org.web3j.abi.datatypes.Utf8String(message),
                        new org.web3j.abi.datatypes.Utf8String(transactionType)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> addTransaction(String contrahent, BigInteger amount) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_ADDTRANSACTION,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(contrahent),
                        new org.web3j.abi.datatypes.generated.Int256(amount)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<TransactionReceipt> createProfile(byte[] userName) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_CREATEPROFILE,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(userName)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteCall<BigInteger> getCumulativeScore(String profileID) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GETCUMULATIVESCORE,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(profileID)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Int256>() {
                }));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteCall<BigInteger> getNoTransactionsReceived(String profileID) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETNOTRANSACTIONSRECEIVED, Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(profileID)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {
                }));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteCall<BigInteger> getNoTransactionsSent(String profileID) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GETNOTRANSACTIONSSENT, Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(profileID)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {
                }));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteCall<Boolean> hasProfile(String userAddress) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_HASPROFILE,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(userAddress)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {
                }));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteCall<Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger>> profiles(String param0) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_PROFILES,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(param0)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {
                }, new TypeReference<Bytes32>() {
                }, new TypeReference<Int256>() {
                }, new TypeReference<Uint256>() {
                }, new TypeReference<Uint256>() {
                }, new TypeReference<Uint256>() {
                }));
        return new RemoteCall<Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger>>(
                new Callable<Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger>>() {
                    @Override
                    public Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger> call()
                            throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple6<String, byte[], BigInteger, BigInteger, BigInteger, BigInteger>(
                                (String) results.get(0).getValue(), (byte[]) results.get(1).getValue(),
                                (BigInteger) results.get(2).getValue(), (BigInteger) results.get(3).getValue(),
                                (BigInteger) results.get(4).getValue(), (BigInteger) results.get(5).getValue());
                    }
                });
    }

    @Deprecated
    public static ReputationRegistry load(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        return new ReputationRegistry(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static ReputationRegistry load(String contractAddress, Web3j web3j, TransactionManager transactionManager,
            BigInteger gasPrice, BigInteger gasLimit) {
        return new ReputationRegistry(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static ReputationRegistry load(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        return new ReputationRegistry(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static ReputationRegistry load(String contractAddress, Web3j web3j, TransactionManager transactionManager,
            ContractGasProvider contractGasProvider) {
        return new ReputationRegistry(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<ReputationRegistry> deploy(Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider, String userRegistryAddress) {
        String encodedConstructor = FunctionEncoder
                .encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(userRegistryAddress)));
        return deployRemoteCall(ReputationRegistry.class, web3j, credentials, contractGasProvider, BINARY,
                encodedConstructor);
    }

    public static RemoteCall<ReputationRegistry> deploy(Web3j web3j, TransactionManager transactionManager,
            ContractGasProvider contractGasProvider, String userRegistryAddress) {
        String encodedConstructor = FunctionEncoder
                .encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(userRegistryAddress)));
        return deployRemoteCall(ReputationRegistry.class, web3j, transactionManager, contractGasProvider, BINARY,
                encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<ReputationRegistry> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice,
            BigInteger gasLimit, String userRegistryAddress) {
        String encodedConstructor = FunctionEncoder
                .encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(userRegistryAddress)));
        return deployRemoteCall(ReputationRegistry.class, web3j, credentials, gasPrice, gasLimit, BINARY,
                encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<ReputationRegistry> deploy(Web3j web3j, TransactionManager transactionManager,
            BigInteger gasPrice, BigInteger gasLimit, String userRegistryAddress) {
        String encodedConstructor = FunctionEncoder
                .encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(userRegistryAddress)));
        return deployRemoteCall(ReputationRegistry.class, web3j, transactionManager, gasPrice, gasLimit, BINARY,
                encodedConstructor);
    }

    public static class ErrorEventEventResponse {
        public Log log;

        public String message;
    }

    public static class GenericTransactionAddedEventResponse {
        public Log log;

        public String sender;

        public String recipient;

        public BigInteger timestamp;

        public String transactionType;

        public String message;

        public String txHash;

        public BigInteger weiAmount;
    }

    public static class TransactionAddedEventResponse {
        public Log log;

        public String sender;

        public String recipient;

        public BigInteger grade;

        public BigInteger recipientNewScore;
    }

    public static class TransactionCountChangedEventResponse {
        public Log log;

        public String recipient;

        public BigInteger newScore;
    }

    public static class TransactionScoreChangedEventResponse {
        public Log log;

        public String sender;

        public String recipient;

        public BigInteger newScore;
    }

    public static class UserProfileCreatedEventResponse {
        public Log log;

        public String owner;

        public byte[] name;
    }
}