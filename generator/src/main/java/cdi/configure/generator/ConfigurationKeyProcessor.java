package cdi.configure.generator;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

import cdi.configure.ConfigurationKey;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ "cdi.configure.ConfigurationKey" })
public class ConfigurationKeyProcessor extends AbstractProcessor {

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		processingEnv.getMessager().printMessage(Kind.NOTE, "called getSupportedAnnotationTypes");
		return Sets.newHashSet(ConfigurationKey.class.getName());
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		processingEnv.getMessager().printMessage(Kind.NOTE, "called getSupportedSourceVersion");
		return SourceVersion.latestSupported();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		// TODO Auto-generated method stub
		processingEnv.getMessager().printMessage(Kind.NOTE, "called processing");
		Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(ConfigurationKey.class);
		if (elementsAnnotatedWith.size() > 1) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "more than one element per type ConfigurationKey");
		}
		for (Element e : elementsAnnotatedWith) {
			if (e.getKind() == ElementKind.ENUM) {
				try {

					TypeElement classElement = (TypeElement) e;
					PackageElement packageElement = (PackageElement) classElement.getEnclosingElement();

					ConfigurationKey annotation = classElement.getAnnotation(ConfigurationKey.class);
					String propertiesPath = annotation.propertiesPath() != null ? annotation.propertiesPath()
							: "/opt/yo/mama.properties";

					String className = classElement.getSimpleName().toString();
					String packageName = packageElement.getQualifiedName().toString();

					
					Builder<String, Object> builder = ImmutableMap.<String, Object> builder()
						.put("enumClass", className)
						.put("packageName", packageName)
						.put("propertiesPath", propertiesPath);
					
					getValidatorMethod(classElement).ifPresent(method -> {
						builder.put("validator", method);
					});
					
					getKeyNullValue(classElement).ifPresent(keyNull -> {
						builder.put("nullValue", keyNull);
					});

					ImmutableMap<String, Object> props = builder.build();
					
					Template config = generateTemplateFor("Configuration.vm", props);
					JavaFileObject configSourceFile = processingEnv.getFiler()
							.createSourceFile(packageName + ".Configuration", e);
					Writer configWriter = configSourceFile.openWriter();
					config.merge(contextFromProperties(props), configWriter);
					configWriter.flush();
					configWriter.close();

					JavaFileObject configBeanSourceFile = processingEnv.getFiler()
							.createSourceFile(packageName + ".ConfigurationBean", e);
					Template configBean = generateTemplateFor("ConfigurationBean.vm", props);
					Writer configBeanWriter = configBeanSourceFile.openWriter();
					configBean.merge(contextFromProperties(props), configBeanWriter);
					configBeanWriter.flush();
					configBeanWriter.close();
				} catch (IOException e1) {
					processingEnv.getMessager().printMessage(Kind.ERROR, "error creating file", e);
				}

			} else {
				processingEnv.getMessager().printMessage(Kind.ERROR, "not an enum type", e);
			}
		}

		return true;
	}

	private Optional<String> getValidatorMethod(TypeElement classElement) {
		return classElement.getEnclosedElements()
			.stream()
			.filter(x -> x.getKind() == ElementKind.METHOD)
			.filter(x -> x.getAnnotation(ConfigurationKey.ConfigValidator.class) != null)
			.map(x -> x.getSimpleName().toString() + "()")
			.findFirst();
	}
	
	private Optional<String> getKeyNullValue(TypeElement classElement) {
		return classElement.getEnclosedElements()
				.stream()
				.filter(x -> x.getKind() == ElementKind.ENUM_CONSTANT)
				.filter(x -> x.getAnnotation(ConfigurationKey.KeyNullValue.class) != null)
				.map(x -> x.getSimpleName().toString())
				.findFirst();
	}
	
	Template generateTemplateFor(String templateName, Map<String, Object> properties) throws IOException {
		Properties props = new Properties();
		URL url = this.getClass().getClassLoader().getResource("velocity.properties");
		props.load(url.openStream());

		VelocityEngine ve = new VelocityEngine(props);
		ve.init();

		return ve.getTemplate(templateName);
	}

	VelocityContext contextFromProperties(Map<String, Object> properties) {
		VelocityContext vc = new VelocityContext();

		for (Entry<String, Object> e : properties.entrySet()) {
			vc.put(e.getKey(), e.getValue());
		}

		return vc;
	}

}