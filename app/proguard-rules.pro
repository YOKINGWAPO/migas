# kotlinx.serialization keeps generated serializers via its bundled rules; nothing extra needed for Room.

# ML Kit instantiates its component registrars by reflection. The bundled rule keeps the classes but,
# under R8 full mode, not their no-arg constructors, so BarcodeScanning.getClient() got a null component.
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
