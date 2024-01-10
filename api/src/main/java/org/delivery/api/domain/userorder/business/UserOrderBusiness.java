package org.delivery.api.domain.userorder.business;

import lombok.RequiredArgsConstructor;
import org.delivery.api.common.annotation.Business;
import org.delivery.api.domain.store.converter.StoreConverter;
import org.delivery.api.domain.store.service.StoreService;
import org.delivery.api.domain.storemenu.converter.StoreMenuConverter;
import org.delivery.api.domain.storemenu.service.StoreMenuService;
import org.delivery.api.domain.user.model.User;
import org.delivery.api.domain.userorder.controller.model.UserOrderDetailResponse;
import org.delivery.api.domain.userorder.controller.model.UserOrderRequest;
import org.delivery.api.domain.userorder.controller.model.UserOrderResponse;
import org.delivery.api.domain.userorder.converter.UserOrderConverter;
import org.delivery.api.domain.userorder.producer.UserOrderProducer;
import org.delivery.api.domain.userorder.service.UserOrderService;
import org.delivery.api.domain.userordermenu.converter.UserOrderMenuConverter;
import org.delivery.api.domain.userordermenu.service.UserOrderMenuService;
import org.delivery.db.store.StoreEntity;
import org.delivery.db.userorder.UserOrderEntity;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Business
public class UserOrderBusiness {

    private final UserOrderService userOrderService;

    private final UserOrderConverter userOrderConverter;
    private final StoreMenuService storeMenuService;

    private final StoreService storeService;

    private final StoreMenuConverter storeMenuConverter;

    private final StoreConverter storeConverter;

    private final UserOrderMenuConverter userOrderMenuConverter;

    private final UserOrderMenuService userOrderMenuService;

    private final UserOrderProducer userOrderProducer;

    // 1. User, menu id
    // 2. generate userOrder
    // 3. generate userOrderMenu
    // 4. generate response
    public UserOrderResponse userOrder(User user, UserOrderRequest body) {

        var storeEntity = storeService.getStoreWithThrow(body.getStoreId());

        var storeMenuEntityList = body.getStoreMenuIdList()
                .stream()
                .map(it -> storeMenuService.getStoreMenuWithThrow(it))
                .collect(Collectors.toList());

        var userOrderEntity = userOrderConverter.toEntity(user, storeEntity, storeMenuEntityList);

        //order
        var newUserOrderEntity = userOrderService.order(userOrderEntity);

        // mapping
        var userOrderMenuEntityList = storeMenuEntityList.stream()
                .map(it -> {
                    var userOrderMenuEntity = userOrderMenuConverter.toEntity(newUserOrderEntity, it);

                    return userOrderMenuEntity;
                })
                .collect(Collectors.toList());

        // write order history
        userOrderMenuEntityList.forEach(it -> {
            userOrderMenuService.order(it);
        });

        // asynchronously send message to message queue
        userOrderProducer.sendOrder(newUserOrderEntity);

        //response
        return userOrderConverter.toResponse(newUserOrderEntity);
    }

    public List<UserOrderDetailResponse> current(User user) {

        var userOrderEntityList = userOrderService.current(user.getId());

        // handle order by order
       var userOrderDetailResponseList = userOrderEntityList.stream().map(it -> {

           // user ordered this menu
           var userOrderMenuEntityList = userOrderMenuService.getUserOrderMenu(it.getId());

           var storeMenuEntityList = userOrderMenuEntityList.stream()
                   .map(userOrderMenuEntity -> {
                       var storeMenuEntity = storeMenuService.getStoreMenuWithThrow(userOrderMenuEntity.getStoreMenu().getId());
               return storeMenuEntity;
           }).collect(Collectors.toList());

           // user ordered from this store TODO need to be refactored
           var storeEntity = storeService.getStoreWithThrow(storeMenuEntityList.stream().findFirst().get().getStore().getId());

           return UserOrderDetailResponse.builder()
                   .userOrderResponse(userOrderConverter.toResponse(it))
                   .storeMenuResponseList(storeMenuConverter.toResponse(storeMenuEntityList))
                   .storeResponse(storeConverter.toResponse(storeEntity))
                   .build()
                   ;
        }).collect(Collectors.toList());


       return userOrderDetailResponseList;
    }

    public List<UserOrderDetailResponse> history(User user) {

        var userOrderEntityList = userOrderService.history(user.getId());

        // handle order by order
        var userOrderDetailResponseList = userOrderEntityList.stream().map(it -> {

            // user ordered this menu
            var userOrderMenuEntityList = userOrderMenuService.getUserOrderMenu(it.getId());

            var storeMenuEntityList = userOrderMenuEntityList.stream()
                    .map(userOrderMenuEntity -> {
                        var storeMenuEntity = storeMenuService.getStoreMenuWithThrow(userOrderMenuEntity.getStoreMenu().getId());
                        return storeMenuEntity;
                    }).collect(Collectors.toList());

            // user ordered from this store TODO need to be refactored
            var storeEntity = storeService.getStoreWithThrow(storeMenuEntityList.stream().findFirst().get().getStore().getId());

            return UserOrderDetailResponse.builder()
                    .userOrderResponse(userOrderConverter.toResponse(it))
                    .storeMenuResponseList(storeMenuConverter.toResponse(storeMenuEntityList))
                    .storeResponse(storeConverter.toResponse(storeEntity))
                    .build()
                    ;
        }).collect(Collectors.toList());


        return userOrderDetailResponseList;
    }

    public UserOrderDetailResponse read(User user, Long orderId) {

        var userOrderEntity = userOrderService.getUserOrderWithoutStatusWithThrow(orderId, user.getId());

        var userOrderMenuEntityList = userOrderMenuService.getUserOrderMenu(userOrderEntity.getId());

        var storeMenuEntityList = userOrderMenuEntityList.stream()
                .map(userOrderMenuEntity -> {
                    var storeMenuEntity = storeMenuService.getStoreMenuWithThrow(userOrderMenuEntity.getStoreMenu().getId());
                    return storeMenuEntity;
                }).collect(Collectors.toList());

        // user ordered from this store TODO need to be refactored
        var storeEntity = storeService.getStoreWithThrow(storeMenuEntityList.stream().findFirst().get().getStore().getId());

        return UserOrderDetailResponse.builder()
                .userOrderResponse(userOrderConverter.toResponse(userOrderEntity))
                .storeMenuResponseList(storeMenuConverter.toResponse(storeMenuEntityList))
                .storeResponse(storeConverter.toResponse(storeEntity))
                .build()
                ;
    }
}