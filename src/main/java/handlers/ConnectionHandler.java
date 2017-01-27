package handlers;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;

import protocol.HttpRequest;
import protocol.HttpResponse;
import protocol.HttpResponseFactory;
import protocol.Protocol;
import protocol.ProtocolException;
import utils.SwsLogger;

/**
 * This class is responsible for handling a incoming request by creating a
 * {@link HttpRequest} object and sending the appropriate response be creating a
 * {@link HttpResponse} object. It implements {@link Runnable} to be used in
 * multi-threaded environment.
 * 
 * @author Chandan R. Rupakheti (rupakhet@rose-hulman.edu)
 */
public class ConnectionHandler implements Runnable {
	private Socket socket;
	private HashMap<String, IRequestHandlerFactory> requestHandlerFactoryMap;

	public ConnectionHandler(Socket socket, HashMap<String, IRequestHandlerFactory> requestHandlerFactoryMap) {
		this.socket = socket;
		this.requestHandlerFactoryMap = requestHandlerFactoryMap;
	}

	/**
	 * The entry point for connection handler. It first parses incoming request
	 * and creates a {@link HttpRequest} object, then it creates an appropriate
	 * {@link HttpResponse} object and sends the response back to the client
	 * (web browser).
	 */
	public void run() {
		InputStream inStream = null;
		OutputStream outStream = null;

		try {
			inStream = this.socket.getInputStream();
			outStream = this.socket.getOutputStream();
		} catch (Exception e) {
			// Cannot do anything if we have exception reading input or output
			// stream
			// May be have text to log this for further analysis?
			SwsLogger.errorLogger.error("Exception while creating socket connections!\n" + e.toString());
			return;
		}

		// At this point we have the input and output stream of the socket
		// Now lets create a HttpRequest object
		HttpRequest request = null;
		HttpResponse response = null;
		try {
			request = HttpRequest.read(inStream);
		} catch (ProtocolException pe) {
			// We have some sort of protocol exception. Get its status code and
			// create response
			// We know only two kind of exception is possible inside
			// fromInputStream
			// Protocol.BAD_REQUEST_CODE and Protocol.NOT_SUPPORTED_CODE
			int status = pe.getStatus();
			if (status == Protocol.BAD_REQUEST_CODE) {
				SwsLogger.accessLogger.info("Bad HTTP request received. Sending 400 Bad Request.");
				response = HttpResponseFactory.create400BadRequest(Protocol.CLOSE);
			} else if (status == Protocol.NOT_SUPPORTED_CODE) {
				SwsLogger.accessLogger.info("Unsupported HTTP request received. Sending 505 Not Supported.");
				response = HttpResponseFactory.create505NotSupported(Protocol.CLOSE);
			}
		} catch (Exception e) {
			SwsLogger.errorLogger.error("Exception occured while trying to read HTTP request!\n" + e.toString());
			// For any other error, we will create bad request response as well
			response = HttpResponseFactory.create400BadRequest(Protocol.CLOSE);
		}

		if (response != null) {
			// Means there was an error, now write the response object to the
			// socket
			try {
				response.write(outStream);
				// System.out.println(response);
			} catch (Exception e) {
				// We will ignore this exception
				SwsLogger.errorLogger.error("Exception occured while sending HTTP resonponse!\n" + e.toString());
			}

			return;
		}

		// We reached here means no error so far, so lets process further
		try {
			// Fill in the code to create a response for version mismatch.
			// You may want to use constants such as Protocol.VERSION,
			// Protocol.NOT_SUPPORTED_CODE, and more.
			// You can check if the version matches as follows
			if (!request.getVersion().equalsIgnoreCase(Protocol.VERSION)) {
				SwsLogger.accessLogger.info("HTTP request received with unsupported version. Sending 400 Bad Request.");
				response = HttpResponseFactory.create400BadRequest(Protocol.CLOSE);
			} else {
				IRequestHandlerFactory factory = this.requestHandlerFactoryMap.get(request.getMethod());
				if (factory == null) {
					SwsLogger.accessLogger.info("HTTP request received for unsupported method. Sending 501 Not Implemented.");
					response = HttpResponseFactory.create501NotImplemented(Protocol.CLOSE);
				} else {
					response = factory.getRequestHandler().handleRequest(request);
				}
			}
		} catch (Exception e) {
			SwsLogger.errorLogger.error(e.toString());
		}
		// So this is a temporary patch for that problem and should be removed
		// after a response object is created for protocol version mismatch.
		if (response == null) {
			SwsLogger.accessLogger.info("Null response created. Sending 400 Bad Request");
			response = HttpResponseFactory.create400BadRequest(Protocol.CLOSE);
		}

		try {
			// Write response and we are all done so close the socket
			response.write(outStream);
			// System.out.println(response);
			socket.close();
		} catch (Exception e) {
			// We will ignore this exception
			SwsLogger.errorLogger.error("Error while writing to socket! \n" + e.toString());
		}
	}
}
