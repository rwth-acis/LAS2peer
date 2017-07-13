package i5.las2peer.restMapper;

import java.util.logging.Level;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import i5.las2peer.logging.L2pLogger;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private final L2pLogger logger = L2pLogger.getInstance(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Throwable e) {
        logger.log(Level.INFO, "Request failed: " + e.toString(), e);
        int code = Status.INTERNAL_SERVER_ERROR.getStatusCode();
        if (e instanceof WebApplicationException) {
            WebApplicationException webEx = (WebApplicationException) e;
            code = webEx.getResponse().getStatus();
        }
        return Response.status(code).entity(new ExceptionEntity(code, e)).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

}