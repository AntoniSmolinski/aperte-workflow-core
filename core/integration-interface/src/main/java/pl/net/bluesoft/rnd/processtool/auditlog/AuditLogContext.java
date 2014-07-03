package pl.net.bluesoft.rnd.processtool.auditlog;

import org.codehaus.jackson.map.ObjectMapper;
import pl.net.bluesoft.rnd.processtool.auditlog.builders.AuditLogBuilder;
import pl.net.bluesoft.rnd.processtool.auditlog.builders.DefaultAuditLogBuilder;
import pl.net.bluesoft.rnd.processtool.auditlog.definition.AuditLogDefinition;
import pl.net.bluesoft.rnd.processtool.auditlog.model.AuditLog;
import pl.net.bluesoft.rnd.processtool.model.IAttributesProvider;
import pl.net.bluesoft.rnd.processtool.ui.widgets.HandlingResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static pl.net.bluesoft.rnd.processtool.plugins.ProcessToolRegistry.Util.getRegistry;

/**
 * User: POlszewski
 * Date: 2014-06-13
 */
public class AuditLogContext {
	private static Logger logger = Logger.getLogger(AuditLogContext.class.getName());
	private static final ThreadLocal<AuditLogBuilder> auditLogBuilder = new ThreadLocal<AuditLogBuilder>();

	public interface Callback {
		void invoke() throws Exception;
	}

	public static AuditLogBuilder get() {
		AuditLogBuilder result = auditLogBuilder.get();
		return result != null ? result : AuditLogBuilder.NULL;
	}

	public static List<HandlingResult> withContext(IAttributesProvider provider, Callback callback) {
		setup(provider);

		try {
			try {
				callback.invoke();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
			List<AuditLog> result = get().toAuditLogs();
			postProcess(provider, result);
			return toHandlingResults(result);
		}
		finally {
			cleanUp();
		}
	}

	private static void setup(IAttributesProvider provider) {
		AuditLogDefinition definition = getDefinition(provider);

		if (definition != null) {
			auditLogBuilder.set(new DefaultAuditLogBuilder(definition));
		}
	}

	private static AuditLogDefinition getDefinition(IAttributesProvider provider) {
		List<AuditLogHandler> handlers = getRegistry().getDataRegistry().getAuditLogHandlers();

		for (AuditLogHandler handler : handlers) {
			AuditLogDefinition definition = handler.getAuditLogDefnition(provider);

			if (definition != null) {
				return definition;
			}
		}
		return null;
	}

	private static void postProcess(IAttributesProvider provider, List<AuditLog> result) {
		if (result.isEmpty()) {
			return;
		}

		List<AuditLogHandler> handlers = getRegistry().getDataRegistry().getAuditLogHandlers();

		for (AuditLogHandler handler : handlers) {
			handler.postProcess(provider, result);
		}
	}

	private static List<HandlingResult> toHandlingResults(List<AuditLog> auditLogs) {
		if (auditLogs.isEmpty()) {
			return Collections.emptyList();
		}

		List<HandlingResult> result = new ArrayList<HandlingResult>(auditLogs.size());
		ObjectMapper mapper = new ObjectMapper();

		for (AuditLog enityLog : auditLogs) {
			if (enityLog.isDifferent()) {
				try {
					result.add(new HandlingResult(new Date(), enityLog.getGroupKey(),
							mapper.writeValueAsString(enityLog.getPre()),
							mapper.writeValueAsString(enityLog.getPost())));
				}
				catch (IOException e) {
					logger.log(Level.INFO, "[AuditLog] Error", e);
				}
			}
		}
		return result;
	}

	private static void cleanUp() {
		auditLogBuilder.remove();
	}
}